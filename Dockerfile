FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

# Copy the shaded JAR (contains all dependencies)
COPY target/atg-tournament-runner-*-shaded.jar runner.jar

ENTRYPOINT ["java", "-jar", "runner.jar"]
