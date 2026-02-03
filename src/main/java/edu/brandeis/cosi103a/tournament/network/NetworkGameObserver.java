package edu.brandeis.cosi103a.tournament.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.event.GameObserver;
import edu.brandeis.cosi.atg.state.GameState;
import edu.brandeis.cosi103a.tournament.network.dto.LogEventRequest;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * GameObserver implementation that forwards all game events to a Network Player Server.
 * This allows the remote server to observe game events for the player.
 */
class NetworkGameObserver implements GameObserver {
    private final String serverUrl;
    private final String playerUuid;
    private final HttpClientWrapper httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructor for NetworkGameObserver.
     *
     * @param serverUrl The base URL of the network player server
     * @param playerUuid The UUID identifying this player session
     * @param httpClient The HTTP client wrapper to use for requests
     * @param objectMapper The Jackson ObjectMapper for JSON serialization
     */
    NetworkGameObserver(String serverUrl, String playerUuid, HttpClientWrapper httpClient, ObjectMapper objectMapper) {
        this.serverUrl = serverUrl;
        this.playerUuid = playerUuid;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void notifyEvent(GameState state, Event event) {
        try {
            // Create request DTO
            LogEventRequest request = new LogEventRequest(state, event, playerUuid);
            String requestJson = objectMapper.writeValueAsString(request);

            // Build HTTP request with timeout
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/log-event"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            // Send request synchronously with backpressure — block until complete or timeout
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("Warning: Failed to log event to server. Status: "
                        + response.statusCode() + " - " + response.body());
            }

        } catch (Exception e) {
            // Log the error but don't throw - observers should not break the game flow
            System.err.println("Warning: Failed to serialize or send event: " + e.getMessage());
        }
    }
}
