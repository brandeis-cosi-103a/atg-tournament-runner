package edu.brandeis.cosi103a.tournament.viewer;

import de.gesundkrank.jskills.GameInfo;
import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.runner.*;
import edu.brandeis.cosi103a.tournament.tape.TapeBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Service for executing tournaments asynchronously without blocking the API.
 */
@Service
public class TournamentExecutionService {

    private final Map<String, TournamentStatus> runningTournaments = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private final Path dataDir;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Progress listener callback interface for tournament execution.
     */
    public interface ProgressListener {
        void onProgress(TournamentStatus status);
    }

    public TournamentExecutionService(
            @Value("${tournament.data-dir:./data}") String dataDir,
            SimpMessagingTemplate messagingTemplate) {
        this.dataDir = Path.of(dataDir);
        this.messagingTemplate = messagingTemplate;
        this.executorService = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
        );
    }

    /**
     * Starts a tournament asynchronously and returns a unique tournament ID.
     *
     * @param config the tournament configuration
     * @param engineLoader the engine loader for creating Engine instances
     * @param progressListener optional callback for progress updates
     * @return unique tournament ID
     */
    public String startTournament(TournamentConfig config, EngineLoader engineLoader,
                                  ProgressListener progressListener) {
        String tournamentId = UUID.randomUUID().toString();

        // Calculate total expected games
        int playersCount = config.players().size();
        int gamesPerPlayer = config.gamesPerPlayer() > 0
            ? config.gamesPerPlayer()
            : RoundGenerator.recommendedGamesPerPlayer(playersCount);
        int gamesPerRound = (playersCount * gamesPerPlayer) / 4;
        int totalGames = config.rounds() * gamesPerRound;

        // Initialize status as QUEUED
        TournamentStatus initialStatus = TournamentStatus.queued(tournamentId, config.rounds(), totalGames);
        runningTournaments.put(tournamentId, initialStatus);

        // Notify listener of initial status
        if (progressListener != null) {
            progressListener.onProgress(initialStatus);
        }

        // Send initial status via WebSocket
        sendWebSocketUpdate(tournamentId, initialStatus);

        // Submit async task
        executorService.submit(() -> executeTournament(tournamentId, config, engineLoader, progressListener));

        return tournamentId;
    }

    /**
     * Gets the current status of a tournament.
     *
     * @param tournamentId the tournament ID
     * @return the tournament status, or empty if not found
     */
    public Optional<TournamentStatus> getTournamentStatus(String tournamentId) {
        return Optional.ofNullable(runningTournaments.get(tournamentId));
    }

    /**
     * Lists all tournaments currently tracked by this service.
     *
     * @return map of tournament IDs to their current status
     */
    public Map<String, TournamentStatus> getAllTournaments() {
        return new HashMap<>(runningTournaments);
    }

    /**
     * Internal method that executes the tournament in a background thread.
     */
    private void executeTournament(String tournamentId, TournamentConfig config,
                                   EngineLoader engineLoader, ProgressListener progressListener) {
        Path outputDir = dataDir.resolve(config.name());
        int completedGames = 0;
        int playersCount = config.players().size();
        int gamesPerPlayer = config.gamesPerPlayer() > 0
            ? config.gamesPerPlayer()
            : RoundGenerator.recommendedGamesPerPlayer(playersCount);
        int gamesPerRound = (playersCount * gamesPerPlayer) / 4;
        int totalGames = config.rounds() * gamesPerRound;

        try {
            // Initialize file writer and write metadata
            RoundFileWriter writer = new RoundFileWriter(outputDir);
            writer.writeTournamentMetadata(config);

            // Create table executor
            TableExecutor tableExecutor = new TableExecutor(engineLoader);
            ExecutorService threadPool = Executors.newFixedThreadPool(
                Math.min(8, Math.max(4, Runtime.getRuntime().availableProcessors()))
            );

            try {
                for (int round = 1; round <= config.rounds(); round++) {
                    // Update status to RUNNING
                    TournamentStatus runningStatus = TournamentStatus.running(
                        tournamentId, round, config.rounds(), completedGames, totalGames
                    );
                    runningTournaments.put(tournamentId, runningStatus);
                    if (progressListener != null) {
                        progressListener.onProgress(runningStatus);
                    }

                    // Send round start update via WebSocket
                    sendWebSocketUpdate(tournamentId, runningStatus);

                    // Check if round already exists (resume support)
                    if (writer.roundExists(round)) {
                        completedGames += gamesPerRound;
                        continue;
                    }

                    // Generate kingdom cards and balanced games
                    List<Card.Type> kingdomCards = RoundGenerator.selectKingdomCards();
                    List<List<PlayerConfig>> games = RoundGenerator.generateBalancedGames(
                        config.players(), gamesPerPlayer);

                    // Run games in parallel
                    List<Future<MatchResult>> futures = new ArrayList<>();
                    for (int g = 0; g < games.size(); g++) {
                        final int gameNum = g + 1;
                        final List<PlayerConfig> gamePlayers = games.get(g);
                        futures.add(threadPool.submit(() ->
                            tableExecutor.executeTable(gameNum, gamePlayers, kingdomCards,
                                1, config.maxTurns()) // 1 game per match
                        ));
                    }

                    // Collect results
                    List<MatchResult> matches = new ArrayList<>();
                    for (Future<MatchResult> future : futures) {
                        matches.add(future.get());
                    }

                    // Write round results
                    Set<String> kingdomCardNames = new HashSet<>();
                    for (Card.Type card : kingdomCards) {
                        kingdomCardNames.add(card.name());
                    }
                    RoundResult roundResult = new RoundResult(round, kingdomCardNames, matches);
                    writer.writeRound(roundResult);

                    // Update completed games count
                    completedGames += gamesPerRound;

                    // Update status after round completion
                    TournamentStatus updatedStatus = TournamentStatus.running(
                        tournamentId, round, config.rounds(), completedGames, totalGames
                    );
                    runningTournaments.put(tournamentId, updatedStatus);
                    if (progressListener != null) {
                        progressListener.onProgress(updatedStatus);
                    }

                    // Send round completion update via WebSocket
                    sendWebSocketUpdate(tournamentId, updatedStatus);
                }
            } finally {
                threadPool.shutdown();
            }

            // Generate tape.json for playback viewer
            try {
                TapeBuilder.buildTape(outputDir, GameInfo.getDefaultGameInfo());
            } catch (Exception tapeEx) {
                System.err.println("Warning: Failed to build tape.json for tournament " + tournamentId + ": " + tapeEx.getMessage());
            }

            // Mark as completed
            TournamentStatus completedStatus = TournamentStatus.completed(
                tournamentId, config.rounds(), totalGames
            );
            runningTournaments.put(tournamentId, completedStatus);
            if (progressListener != null) {
                progressListener.onProgress(completedStatus);
            }

            // Send completion update via WebSocket
            sendWebSocketUpdate(tournamentId, completedStatus);

        } catch (Exception e) {
            // Mark as failed with error message
            TournamentStatus failedStatus = TournamentStatus.failed(
                tournamentId,
                0,
                config.rounds(),
                completedGames,
                totalGames,
                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
            );
            runningTournaments.put(tournamentId, failedStatus);
            if (progressListener != null) {
                progressListener.onProgress(failedStatus);
            }

            // Send failure update via WebSocket
            sendWebSocketUpdate(tournamentId, failedStatus);
        }
    }

    /**
     * Sends a tournament status update via WebSocket to all subscribers.
     *
     * @param tournamentId the tournament ID
     * @param status the current status to broadcast
     */
    private void sendWebSocketUpdate(String tournamentId, TournamentStatus status) {
        try {
            messagingTemplate.convertAndSend("/topic/tournaments/" + tournamentId, status);
        } catch (Exception e) {
            // Log error but don't fail tournament execution
            System.err.println("Failed to send WebSocket update for tournament " + tournamentId + ": " + e.getMessage());
        }
    }

    /**
     * Shuts down the executor service. Call this when the application is shutting down.
     */
    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
