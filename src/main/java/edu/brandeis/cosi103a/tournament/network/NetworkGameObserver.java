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
import java.util.concurrent.CompletableFuture;

/**
 * GameObserver implementation that forwards game events to a Network Player Server
 * asynchronously. Events are sent in order per player via CompletableFuture chaining,
 * but the calling thread is not blocked.
 *
 * <p>Call {@link #flush()} before asking the player to make a decision to ensure
 * all queued events have been delivered.
 */
class NetworkGameObserver implements GameObserver {
    private final String playerName;
    private final String serverUrl;
    private final String playerUuid;
    private final HttpClientWrapper httpClient;
    private final ObjectMapper objectMapper;

    /** Chain of pending async sends — ensures per-player event ordering. */
    private CompletableFuture<Void> eventChain = CompletableFuture.completedFuture(null);

    NetworkGameObserver(String playerName, String serverUrl, String playerUuid, HttpClientWrapper httpClient, ObjectMapper objectMapper) {
        this.playerName = playerName;
        this.serverUrl = serverUrl;
        this.playerUuid = playerUuid;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void notifyEvent(GameState state, Event event) {
        // Serialize on the calling thread (fast, no I/O)
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(new LogEventRequest(state, event, playerUuid));
        } catch (Exception e) {
            System.err.println("Warning: [" + playerName + " @ " + serverUrl + "] Failed to serialize event: " + e.getMessage());
            return;
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/log-event"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        // Chain after previous send to preserve ordering; returns immediately
        eventChain = eventChain.thenCompose(v ->
                httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() != 200) {
                                String body = response.body();
                                if (body != null && body.length() > 200) body = body.substring(0, 200) + "...";
                                System.err.println("Warning: [" + playerName + " @ " + serverUrl
                                        + "] /log-event returned " + response.statusCode() + " - " + body);
                            }
                        })
                        .exceptionally(e -> {
                            System.err.println("Warning: [" + playerName + " @ " + serverUrl
                                    + "] /log-event request failed: " + e.getClass().getSimpleName()
                                    + ": " + e.getMessage());
                            return null;
                        })
        );
    }

    /**
     * Blocks until all queued events have been delivered.
     * Call this before asking the player for a decision.
     */
    void flush() {
        eventChain.join();
    }
}
