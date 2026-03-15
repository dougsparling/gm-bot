# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GM Bot is a Slack bot for tabletop RPG game masters with two slash commands:
- `/roll` — Dice rolling with a custom DSL (e.g., `4d6 drop lowest`, `d20+15`, `7d10 reroll 1 to 2`)
- `/rule <ruleset> <question>` — LLM-powered rulebook assistant using local markdown files

## Build & Run

Requires Java 21+ and SBT.

```sh
sbt run           # Start dev server on port 8080
sbt test          # Run tests
sbt assembly      # Build fat JAR
docker build -t gm-bot .   # Docker build (multi-stage)
```

Deploy to Kubernetes via `manifest/deployment.yaml`.

## Environment Variables

| Variable | Purpose |
|---|---|
| `SLACK_SIGNING_SECRET` | Required — verifies Slack request signatures |
| `LLM_BASE_URL` | OpenAI-compatible endpoint URL (e.g. Gemini, DashScope, Ollama) |
| `LLM_API_KEY` | API key for the configured endpoint |
| `LLM_MODEL` | Model name (e.g. `gemini-2.0-flash`, `qwen-plus`) |
| `RULEBOOKS_PATH` | Path to rulebooks directory (default: `./rulebooks`) |
| `PORT` | HTTP port (default: 8080) |

## Architecture

```
HTTP Request → SlackSignatureFilter → GmBotServlet → /roll or /rule
                                                          ↓               ↓
                                              RollSpecParser         RulebookFinder
                                                    ↓                      ↓
                                              RollSpec (AST)          RuleOracle
                                              RollSpecRunner          (LangChain4j
                                             (or Approximator)        OpenAI-compat)
                                                    ↓                      ↓
                                              JSON response       POST to response_url
```

**Key source files** (`src/main/scala/ca/dougsparling/`):

- **GmBotServlet.scala** — Main servlet; handles `/roll` and `/rule` endpoints
- **SlackSignatureFilter.scala** — Verifies Slack HMAC signatures before any endpoint
- **RollSpecParser.scala** — Parser combinator for the dice roll DSL
- **RollSpec.scala** — AST for parsed roll specs
- **RollSpecRunner.scala** — Executes rolls; handles reroll and drop-lowest/highest logic
- **RollSpecApproximator.scala** — For large rolls, approximates via Central Limit Theorem (Gaussian sampling)
- **Dice.scala** — `Dice` trait, `SecureDice` (production), `FixedDice` (tests)
- **Result.scala** — `Result`, `Batch`, `Roll`, `Fate` (Kept/Rerolled/Dropped)
- **RuleOracle.scala** — LangChain4j `OpenAiChatModel` wrapper; provides `readFile`, `searchFiles` tools to the LLM via a manual tool-calling loop
- **RulebookFinder.scala** — Resolves rulebook directory by prefix; returns `Found`, `Ambiguous`, or `NotFound`

## Rulebooks

Markdown files live in `./rulebooks/<ruleset>/` (e.g., `rulebooks/5e/`). Each directory has an `index.md` that the LLM reads to route queries to the right file.

## LLM Integration

Uses **LangChain4j 1.0.0** (`dev.langchain4j:langchain4j-open-ai`) with any OpenAI-compatible endpoint. The model is configured entirely via env vars (`LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL`), so no code changes are needed to switch between Gemini, DashScope (Qwen), or local Ollama.
