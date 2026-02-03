package edu.brandeis.cosi103a.tournament.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import edu.brandeis.cosi.atg.decisions.Decision;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.event.GameObserver;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi.atg.state.GameState;
import edu.brandeis.cosi103a.tournament.network.config.ObjectMapperFactory;
import edu.brandeis.cosi103a.tournament.network.dto.DecisionRequest;
import edu.brandeis.cosi103a.tournament.network.dto.DecisionResponse;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

/**
 * A Player implementation that acts as a client to a Network Player Server.
 * Delegates all decision-making and event observation to a remote server via HTTP.
 */
public class NetworkPlayer implements Player {
    private final String name;
    private final String serverUrl;
    private final String playerUuid;
    private final HttpClientWrapper httpClient;
    private final ObjectMapper objectMapper;
    private final NetworkGameObserver observer;

    /**
     * Constructor for NetworkPlayer.
     *
     * @param name The name of this player
     * @param serverUrl The base URL of the network player server (e.g., "http://localhost:8080")
     */
    public NetworkPlayer(String name, String serverUrl) {
        this.name = name;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.playerUuid = UUID.randomUUID().toString();
        this.httpClient = new HttpClientWrapper.Default();

        // Use shared ObjectMapper configuration
        this.objectMapper = ObjectMapperFactory.create();

        this.observer = new NetworkGameObserver(this.serverUrl, this.playerUuid, this.httpClient, this.objectMapper);
    }

    /**
     * Constructor for testing with custom HTTP client.
     */
    NetworkPlayer(String name, String serverUrl, HttpClientWrapper httpClient, ObjectMapper objectMapper) {
        this.name = name;
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.playerUuid = UUID.randomUUID().toString();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.observer = new NetworkGameObserver(this.serverUrl, this.playerUuid, this.httpClient, this.objectMapper);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Decision makeDecision(GameState state, ImmutableList<Decision> options, Optional<Event> event) {
        try {
            // Create request DTO
            DecisionRequest request = new DecisionRequest(state, options, event, playerUuid);
            String requestJson = objectMapper.writeValueAsString(request);

            // Build HTTP request
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/decide"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            // Send request and get response
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Server returned error: " + response.statusCode() + " - " + response.body());
            }

            // Parse response
            DecisionResponse decisionResponse = objectMapper.readValue(response.body(), DecisionResponse.class);

            // Return the decision from the response
            return decisionResponse.getDecision();

        } catch (Exception e) {
            throw new RuntimeException("Failed to get decision from network player server", e);
        }
    }

    @Override
    public Optional<GameObserver> getObserver() {
        return Optional.of(observer);
    }

    /**
     * Get the UUID assigned to this player instance.
     * @return the player UUID
     */
    public String getPlayerUuid() {
        return playerUuid;
    }
}
