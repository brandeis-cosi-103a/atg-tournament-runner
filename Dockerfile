FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

# Copy the shaded JAR (contains all dependencies)
COPY target/atg-tournament-runner-*-shaded.jar runner.jar

# Copy the pre-built reference engine JAR
COPY engine.jar engine.jar

# Default engine configuration (can be overridden at runtime)
ENV TOURNAMENT_ENGINE_JAR=/app/engine.jar
ENV TOURNAMENT_ENGINE_CLASS=edu.brandeis.cosi103a.engine.GameEngine

# Expose port for the web viewer
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "runner.jar"]
