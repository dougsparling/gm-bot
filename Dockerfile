FROM eclipse-temurin:21-jre-alpine
ADD "target/scala-3.8.2/gm-bot-assembly-1.0.0.jar" /usr/share/gmbot/gmbot.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/usr/share/gmbot/gmbot.jar"]