package edu.brandeis.cosi103a.tournament.runner;

import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi103a.tournament.engine.EngineLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Main entry point for the tournament runner CLI.
 *
 * <p>Invocation:
 * <pre>
 * java -jar atg-tournament-runner.jar \
 *   /path/to/engine.jar com.example.MyEngine \
 *   --name spring-2026 --rounds 15 --games-per-table 50 \
 *   --output ./data \
 *   --player Alice=https://alice:8080 \
 *   --player NaiveMoney=naive-money \
 *   --player ActionHeavy=action-heavy \
 *   --player Random=random
 * </pre>
 *
 * <p>Docker usage:
 * <pre>
 * docker run --rm -v $(pwd):/jars -v $(pwd)/results:/data \
 *   ghcr.io/brandeis-cosi-103a/atg-tournament-runner \
 *   /jars/my-engine.jar com.student.MyEngine \
 *   --name practice --rounds 3 --games-per-table 10 \
 *   --output /data \
 *   --player Student=https://my-player.azurewebsites.net \
 *   --player Bot1=naive-money \
 *   --player Bot2=action-heavy \
 *   --player Bot3=random
 * </pre>
 */
public class TournamentRunner {

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String engineJar = args[0];
        String engineClass = args[1];

        // Parse remaining args (skip first two positional args)
        String[] remainingArgs = new String[args.length - 2];
        System.arraycopy(args, 2, remainingArgs, 0, args.length - 2);

        TournamentConfig config = parseArgs(remainingArgs);
        Path outputDir = Path.of(config.name()).isAbsolute()
            ? Path.of(config.name())
            : parseOutputDir(remainingArgs).resolve(config.name());

        try {
            EngineLoader engineLoader = new EngineLoader(engineJar, engineClass);
            runTournament(config, outputDir, engineLoader);
        } catch (Exception e) {
            System.err.println("Tournament failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static void runTournament(TournamentConfig config, Path outputDir, EngineLoader engineLoader) throws Exception {
        RoundFileWriter writer = new RoundFileWriter(outputDir);
        writer.writeTournamentMetadata(config);

        TableExecutor tableExecutor = new TableExecutor(engineLoader);
        ExecutorService threadPool = Executors.newFixedThreadPool(
            Math.min(8, Math.max(4, Runtime.getRuntime().availableProcessors()))
        );

        try {
            for (int round = 1; round <= config.rounds(); round++) {
                // Resume support: skip existing rounds
                if (writer.roundExists(round)) {
                    System.out.printf("Round %d already exists, skipping%n", round);
                    continue;
                }

                System.out.printf("Starting round %d/%d%n", round, config.rounds());

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

                java.util.Set<String> kingdomCardNames = kingdomCards.stream()
                    .map(Card.Type::name)
                    .collect(Collectors.toSet());

                RoundResult roundResult = new RoundResult(round, kingdomCardNames, matches);
                writer.writeRound(roundResult);

                System.out.printf("Round %d complete (%d tables, %d games each)%n",
                    round, tables.size(), config.gamesPerTable());
            }
        } finally {
            threadPool.shutdown();
        }

        System.out.printf("Tournament complete! Results written to %s%n", outputDir);
    }

    /**
     * Parses CLI arguments into a TournamentConfig.
     *
     * @throws IllegalArgumentException if required arguments are missing
     */
    static TournamentConfig parseArgs(String[] args) {
        String name = null;
        Integer rounds = null;
        Integer gamesPerTable = null;
        int maxTurns = 100;
        List<PlayerConfig> players = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--name" -> name = args[++i];
                case "--rounds" -> rounds = Integer.parseInt(args[++i]);
                case "--games-per-table" -> gamesPerTable = Integer.parseInt(args[++i]);
                case "--max-turns" -> maxTurns = Integer.parseInt(args[++i]);
                case "--output" -> i++; // consumed but stored separately
                case "--player" -> {
                    String spec = args[++i];
                    int eq = spec.indexOf('=');
                    if (eq < 0) {
                        throw new IllegalArgumentException("Invalid player spec: " + spec);
                    }
                    String playerName = spec.substring(0, eq);
                    String playerUrl = spec.substring(eq + 1);
                    String playerId = playerName.toLowerCase();
                    players.add(new PlayerConfig(playerId, playerName, playerUrl));
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        if (name == null || rounds == null || gamesPerTable == null) {
            throw new IllegalArgumentException("Missing required arguments: --name, --rounds, --games-per-table");
        }
        if (players.size() < 3) {
            throw new IllegalArgumentException("Need at least 3 players");
        }

        return new TournamentConfig(name, rounds, gamesPerTable, maxTurns, players);
    }

    private static Path parseOutputDir(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--output".equals(args[i])) {
                return Path.of(args[i + 1]);
            }
        }
        return Path.of("./data");
    }

    private static void printUsage() {
        System.err.println("Usage: java -jar atg-tournament-runner.jar <engine-jar> <engine-class> [options]");
        System.err.println();
        System.err.println("Arguments:");
        System.err.println("  <engine-jar>       Path to the JAR file containing the Engine implementation");
        System.err.println("  <engine-class>     Fully-qualified class name of the Engine implementation");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  --name <name>              Tournament name (required)");
        System.err.println("  --rounds <n>               Number of rounds to play (required)");
        System.err.println("  --games-per-table <n>      Games per table per round (required)");
        System.err.println("  --output <dir>             Output directory (default: ./data)");
        System.err.println("  --max-turns <n>            Max turns per game (default: 100)");
        System.err.println("  --player <Name>=<url>      Add a player (at least 3 required)");
        System.err.println();
        System.err.println("Player URLs:");
        System.err.println("  https://...                Network player URL");
        System.err.println("  naive-money                Built-in naive money bot");
        System.err.println("  action-heavy               Built-in action-focused bot");
        System.err.println("  random                     Built-in random bot");
        System.err.println();
        System.err.println("Example:");
        System.err.println("  java -jar atg-tournament-runner.jar \\");
        System.err.println("    /path/to/engine.jar com.example.MyEngine \\");
        System.err.println("    --name test --rounds 3 --games-per-table 10 \\");
        System.err.println("    --player Alice=https://alice.example.com \\");
        System.err.println("    --player Bot1=naive-money \\");
        System.err.println("    --player Bot2=random");
    }
}
