package ca.dougsparling.gmbot.rulebook;

import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class RuleOracle {

    private static final Logger logger = LoggerFactory.getLogger(RuleOracle.class);

    private static final String MODEL = "gemini-3.1-flash-lite-preview";

    private static final String SYSTEM_PROMPT =
        "You are a tabletop RPG rules assistant. You have tools to read files from a rulebook directory. " +
        "Start by calling listFiles(\"\") to see what's available, then read \"index.md\" to understand the structure. " +
        "Read relevant section files to answer the question accurately and concisely. " +
        "If the rulebook does not cover the question, say so clearly. Do not invent rules. " +
        "Format your response using Slack mrkdwn: *bold* (not **bold**), _italic_ (not *italic*), " +
        "`code`, ```code blocks```, and • for bullet points.";

    private final Path rulebookPath;

    public RuleOracle(Path rulebookPath) {
        this.rulebookPath = rulebookPath.toAbsolutePath().normalize();
    }

    @Schema(name = "readFile", description = "Reads a file from the rulebook directory")
    public String readFile(
        @Schema(name = "filename", description = "Relative path to the file within the rulebook directory") String filename
    ) {
        try {
            Path resolved = rulebookPath.resolve(filename).normalize();
            if (!resolved.startsWith(rulebookPath)) {
                return "Error: access denied — path is outside the rulebook directory.";
            }
            if (!Files.isRegularFile(resolved)) {
                return "Error: file not found: " + filename;
            }
            return Files.readString(resolved);
        } catch (IOException e) {
            logger.warn("readFile failed for {}: {}", filename, e.getMessage());
            return "Error reading file: " + e.getMessage();
        }
    }

    @Schema(name = "listFiles", description = "Lists files in the rulebook directory or a subdirectory")
    public String listFiles(
        @Schema(name = "subdirectory", description = "Subdirectory to list; use empty string for the root") String subdirectory
    ) {
        try {
            Path target = subdirectory.isEmpty()
                ? rulebookPath
                : rulebookPath.resolve(subdirectory).normalize();
            if (!target.startsWith(rulebookPath)) {
                return "Error: access denied — path is outside the rulebook directory.";
            }
            if (!Files.isDirectory(target)) {
                return "Error: not a directory: " + subdirectory;
            }
            return Files.list(target)
                .map(p -> p.getFileName().toString() + (Files.isDirectory(p) ? "/" : ""))
                .sorted()
                .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            logger.warn("listFiles failed for {}: {}", subdirectory, e.getMessage());
            return "Error listing files: " + e.getMessage();
        }
    }

    public void ask(String question, String responseUrl, String preface) {
        try {
            LlmAgent agent = LlmAgent.builder()
                .model(MODEL)
                .name("rulebook_assistant")
                .description("Answers tabletop RPG rules questions from local rulebook files")
                .instruction(SYSTEM_PROMPT)
                .tools(
                    FunctionTool.create(this, "listFiles"),
                    FunctionTool.create(this, "readFile")
                )
                .build();

            InMemoryRunner runner = new InMemoryRunner(agent);
            Session session = runner.sessionService()
                .createSession(agent.name(), "slack-user")
                .blockingGet();

            Content message = Content.fromParts(Part.fromText(question));

            List<Event> events = runner.runAsync("slack-user", session.id(), message)
                .toList()
                .blockingGet();

            String answer = events.stream()
                .filter(Event::finalResponse)
                .map(Event::stringifyContent)
                .findFirst()
                .orElse("I could not find an answer in the rulebook.");
            postToSlack(responseUrl, preface + "\n" + answer);

        } catch (Exception e) {
            logger.error("RuleOracle.ask failed", e);
            postToSlack(responseUrl, preface + "\nSorry, I encountered an error looking up the rules: " + e.getMessage());
        }
    }

    private void postToSlack(String responseUrl, String text) {
        String json = "{\"response_type\":\"in_channel\",\"text\":" + jsonString(text) + "}";
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(responseUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Slack response_url POST status: {}", response.statusCode());
        } catch (Exception e) {
            logger.error("Failed to POST to Slack response_url", e);
        }
    }

    /** Minimal JSON string escaping — no extra library needed. */
    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append("\"").toString();
    }
}
