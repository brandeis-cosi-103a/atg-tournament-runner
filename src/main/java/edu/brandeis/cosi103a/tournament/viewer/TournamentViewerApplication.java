package edu.brandeis.cosi103a.tournament.viewer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main application class for the Tournament Viewer.
 * Serves a web-based animated leaderboard for tournament results.
 */
@SpringBootApplication
public class TournamentViewerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TournamentViewerApplication.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
