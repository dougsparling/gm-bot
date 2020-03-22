FROM openjdk:8-jre
ADD "target/scala-2.12/gm-bot-assembly-1.0.0.jar" /usr/share/gmbot/gmbot.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/usr/share/gmbot/gmbot.jar"]