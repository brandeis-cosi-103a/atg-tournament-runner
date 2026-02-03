package edu.brandeis.cosi103a.tournament.viewer;

import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi103a.tournament.engine.EngineLoader;
import edu.brandeis.cosi103a.tournament.runner.*;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Progress listener callback interface for tournament execution.
     */
    public interface ProgressListener {
        void onProgress(TournamentStatus status);
    }

    public TournamentExecutionService(@Value("${tournament.data-dir:./data}") String dataDir) {
        this.dataDir = Path.of(dataDir);
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
        int tablesPerRound = (playersCount + 3) / 4; // Approximate tables per round
        int totalGames = config.rounds() * tablesPerRound * config.gamesPerTable();

        // Initialize status as QUEUED
        TournamentStatus initialStatus = TournamentStatus.queued(tournamentId, config.rounds(), totalGames);
        runningTournaments.put(tournamentId, initialStatus);

        // Notify listener of initial status
        if (progressListener != null) {
            progressListener.onProgress(initialStatus);
        }

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
        int tablesPerRound = (playersCount + 3) / 4;
        int totalGames = config.rounds() * tablesPerRound * config.gamesPerTable();

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

                    // Check if round already exists (resume support)
                    if (writer.roundExists(round)) {
                        completedGames += tablesPerRound * config.gamesPerTable();
                        continue;
                    }

                    // Generate kingdom cards and tables
                    List<Card.Type> kingdomCards = RoundGenerator.selectKingdomCards();
                    List<List<PlayerConfig>> tables = RoundGenerator.shuffleIntoTables(config.players());

                    // Run tables in parallel
                    List<Future<MatchResult>> futures = new ArrayList<>();
                    for (int t = 0; t < tables.size(); t++) {
                        final int tableNum = t + 1;
                        final List<PlayerConfig> tablePlayers = tables.get(t);
                        futures.add(threadPool.submit(() ->
                            tableExecutor.executeTable(tableNum, tablePlayers, kingdomCards,
                                config.gamesPerTable(), config.maxTurns())
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
                    completedGames += tables.size() * config.gamesPerTable();

                    // Update status after round completion
                    TournamentStatus updatedStatus = TournamentStatus.running(
                        tournamentId, round, config.rounds(), completedGames, totalGames
                    );
                    runningTournaments.put(tournamentId, updatedStatus);
                    if (progressListener != null) {
                        progressListener.onProgress(updatedStatus);
                    }
                }
            } finally {
                threadPool.shutdown();
            }

            // Mark as completed
            TournamentStatus completedStatus = TournamentStatus.completed(
                tournamentId, config.rounds(), totalGames
            );
            runningTournaments.put(tournamentId, completedStatus);
            if (progressListener != null) {
                progressListener.onProgress(completedStatus);
            }

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
        }
    }

    /**
     * Shuts down the executor service. Call this when the application is shutting down.
     */
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
