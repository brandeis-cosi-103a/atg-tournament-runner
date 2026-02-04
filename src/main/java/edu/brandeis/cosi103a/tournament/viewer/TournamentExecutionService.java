package edu.brandeis.cosi103a.tournament.viewer;

import de.gesundkrank.jskills.GameInfo;
import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.runner.*;
import edu.brandeis.cosi103a.tournament.tape.TapeBuilder;
import edu.brandeis.cosi103a.tournament.tape.TrueSkillTracker;
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
    private final int gameThreadPoolSize;

    /**
     * Progress listener callback interface for tournament execution.
     */
    public interface ProgressListener {
        void onProgress(TournamentStatus status);
    }

    public TournamentExecutionService(
            @Value("${tournament.data-dir:./data}") String dataDir,
            @Value("${tournament.game-thread-pool-size:64}") int gameThreadPoolSize,
            SimpMessagingTemplate messagingTemplate) {
        this.dataDir = Path.of(dataDir);
        this.gameThreadPoolSize = gameThreadPoolSize;
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
     * Metadata for a submitted game task.
     */
    private record GameTask(int round, int gameNum, List<Card.Type> kingdomCards) {}

    /**
     * Result of a game with its metadata.
     */
    private record GameResultWithMeta(int round, MatchResult result, Set<String> kingdomCardNames) {}

    /**
     * Internal method that executes the tournament in a background thread.
     * All games across all rounds are submitted upfront for maximum parallelism.
     */
    private void executeTournament(String tournamentId, TournamentConfig config,
                                   EngineLoader engineLoader, ProgressListener progressListener) {
        Path outputDir = dataDir.resolve(config.name());
        int completedGames = 0;
        int playersCount = config.players().size();
        int gamesPerPlayer = config.gamesPerPlayer() > 0
            ? config.gamesPerPlayer()
            : RoundGenerator.recommendedGamesPerPlayer(playersCount);
        gamesPerPlayer = RoundGenerator.adjustGamesPerPlayer(playersCount, gamesPerPlayer);
        int gamesPerRound = (playersCount * gamesPerPlayer) / 4;
        int totalGames = config.rounds() * gamesPerRound;

        try {
            // Initialize file writer and write metadata
            RoundFileWriter writer = new RoundFileWriter(outputDir);
            writer.writeTournamentMetadata(config);

            // Create table executor
            TableExecutor tableExecutor = createTableExecutor(engineLoader);
            // Configurable pool size for I/O-bound workloads (network players spend time waiting)
            ExecutorService threadPool = Executors.newFixedThreadPool(gameThreadPoolSize);

            // Initialize TrueSkill tracker for live rating updates
            List<String> playerIds = config.players().stream()
                .map(PlayerConfig::id)
                .toList();
            TrueSkillTracker ratingsTracker = new TrueSkillTracker(playerIds, GameInfo.getDefaultGameInfo());

            // Pre-generate all rounds' data and track which rounds to skip (resume support)
            Map<Integer, List<Card.Type>> roundKingdomCards = new HashMap<>();
            Map<Integer, List<List<PlayerConfig>>> roundGames = new HashMap<>();
            Set<Integer> skippedRounds = new HashSet<>();

            for (int round = 1; round <= config.rounds(); round++) {
                if (writer.roundExists(round)) {
                    skippedRounds.add(round);
                    completedGames += gamesPerRound;
                } else {
                    roundKingdomCards.put(round, RoundGenerator.selectKingdomCards());
                    roundGames.put(round, RoundGenerator.generateBalancedGames(config.players(), gamesPerPlayer));
                }
            }

            // Send initial status
            TournamentStatus initialStatus = TournamentStatus.running(
                tournamentId, 1, config.rounds(), completedGames, totalGames, null
            );
            runningTournaments.put(tournamentId, initialStatus);
            if (progressListener != null) {
                progressListener.onProgress(initialStatus);
            }
            sendWebSocketUpdate(tournamentId, initialStatus);

            // Submit ALL games from ALL rounds upfront
            CompletionService<GameResultWithMeta> completionService =
                new ExecutorCompletionService<>(threadPool);

            // Stagger initial submissions to achieve smooth completion rate.
            // If games take ~T seconds and we have N threads, stagger by T/N.
            // Assuming games take ~3 seconds, with 64 threads: 3000/64 ≈ 47ms
            int staggerDelayMs = 50; // ms between submissions for first batch

            int submittedGames = 0;
            for (int round = 1; round <= config.rounds(); round++) {
                if (skippedRounds.contains(round)) continue;

                List<Card.Type> kingdomCards = roundKingdomCards.get(round);
                List<List<PlayerConfig>> games = roundGames.get(round);
                Set<String> kingdomCardNames = new HashSet<>();
                for (Card.Type card : kingdomCards) {
                    kingdomCardNames.add(card.name());
                }

                for (int g = 0; g < games.size(); g++) {
                    final int roundNum = round;
                    final int gameNum = g + 1;
                    final List<PlayerConfig> gamePlayers = games.get(g);
                    final List<Card.Type> kc = kingdomCards;
                    final Set<String> kcNames = kingdomCardNames;

                    completionService.submit(() -> {
                        MatchResult result = tableExecutor.executeTable(gameNum, gamePlayers, kc,
                            1, config.maxTurns());
                        return new GameResultWithMeta(roundNum, result, kcNames);
                    });
                    submittedGames++;

                    // Stagger first batch of submissions (one per thread)
                    if (submittedGames <= gameThreadPoolSize) {
                        try {
                            Thread.sleep(staggerDelayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            // Track results per round for writing round files
            Map<Integer, List<MatchResult>> roundResults = new HashMap<>();
            Map<Integer, Integer> roundCompletedCount = new HashMap<>();
            for (int round = 1; round <= config.rounds(); round++) {
                if (!skippedRounds.contains(round)) {
                    roundResults.put(round, new ArrayList<>());
                    roundCompletedCount.put(round, 0);
                }
            }

            // Process results as they complete (in any order)
            int currentRoundForDisplay = skippedRounds.contains(1) ? 2 : 1;
            try {
                for (int i = 0; i < submittedGames; i++) {
                    GameResultWithMeta gameResult = completionService.take().get();
                    int round = gameResult.round();
                    MatchResult result = gameResult.result();

                    // Track result for round file
                    roundResults.get(round).add(result);
                    roundCompletedCount.merge(round, 1, Integer::sum);

                    // Update ratings
                    if (!result.outcomes().isEmpty()) {
                        ratingsTracker.processGame(result.outcomes().get(0).placements());
                    }
                    completedGames++;

                    // Update display round (show highest round with any progress)
                    if (round > currentRoundForDisplay) {
                        currentRoundForDisplay = round;
                    }

                    // Send WebSocket update
                    Map<String, Double> currentRatings = buildRatingsMap(ratingsTracker);
                    TournamentStatus status = TournamentStatus.running(
                        tournamentId, currentRoundForDisplay, config.rounds(), completedGames, totalGames, currentRatings
                    );
                    sendWebSocketUpdate(tournamentId, status);

                    // Write round file if round is complete
                    int expectedGames = roundGames.get(round).size();
                    if (roundCompletedCount.get(round) == expectedGames) {
                        Set<String> kcNames = gameResult.kingdomCardNames();
                        RoundResult roundResultObj = new RoundResult(round, kcNames, roundResults.get(round));
                        writer.writeRound(roundResultObj);
                    }
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

            // Mark as completed with final ratings
            Map<String, Double> finalRatings = buildRatingsMap(ratingsTracker);
            TournamentStatus completedStatus = TournamentStatus.completed(
                tournamentId, config.rounds(), totalGames, finalRatings
            );
            runningTournaments.put(tournamentId, completedStatus);
            if (progressListener != null) {
                progressListener.onProgress(completedStatus);
            }

            // Send completion update via WebSocket
            sendWebSocketUpdate(tournamentId, completedStatus);

        } catch (Exception e) {
            // Log the error for debugging
            System.err.println("Tournament " + tournamentId + " failed: " + e.getMessage());
            e.printStackTrace(System.err);

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
     * Converts TrueSkillTracker ratings to display format (conservative rating, rounded).
     *
     * @param tracker the TrueSkill tracker with current ratings
     * @return map of player IDs to conservative ratings (mu - 3*sigma), rounded to 1 decimal
     */
    private Map<String, Double> buildRatingsMap(TrueSkillTracker tracker) {
        Map<String, Double> result = new HashMap<>();
        for (String playerId : tracker.getCurrentRatings().keySet()) {
            double conservative = tracker.getConservativeRating(playerId);
            result.put(playerId, Math.round(conservative * 10.0) / 10.0);
        }
        return result;
    }

    /**
     * Creates the TableExecutor for running games.
     * Can be overridden in tests to inject custom behavior (e.g., DelayedPlayerWrapper).
     *
     * @param engineLoader the engine loader to use
     * @return a TableExecutor instance
     */
    protected TableExecutor createTableExecutor(EngineLoader engineLoader) {
        return new TableExecutor(engineLoader);
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
