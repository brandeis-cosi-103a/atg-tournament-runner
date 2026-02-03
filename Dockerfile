FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

# Copy the shaded JAR (contains all dependencies)
COPY target/atg-tournament-runner-*-shaded.jar runner.jar

# Expose port for the web viewer
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "runner.jar"]
