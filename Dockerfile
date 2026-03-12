FROM eclipse-temurin:21-jdk-alpine AS builder
RUN apk add --no-cache bash curl && \
    curl -L "https://github.com/sbt/sbt/releases/download/v1.12.5/sbt-1.12.5.tgz" | tar xz -C /usr/local
ENV PATH="/usr/local/sbt/bin:$PATH"
WORKDIR /build

# Copy only build definition files and download dependencies.
# This layer is cached as long as build.sbt and project/ don't change.
COPY build.sbt .
COPY project/ project/
RUN sbt update

# Now copy source and build the fat jar.
COPY src/ src/
RUN sbt assembly

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /build/target/scala-3.8.2/gm-bot-assembly-1.0.0.jar /usr/share/gmbot/gmbot.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/usr/share/gmbot/gmbot.jar"]
