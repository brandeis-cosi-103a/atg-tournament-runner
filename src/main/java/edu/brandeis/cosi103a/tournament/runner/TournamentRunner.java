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
 *   --name spring-2026 --rounds 15 --games-per-player 12 \
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
 *   --name practice --rounds 3 --games-per-player 3 \
 *   --output /data \
 *   --player Student=https://my-player.azurewebsites.net \
 *   --player Bot1=naive-money \
 *   --player Bot2=action-heavy \
 *   --player Bot3=random
 * </pre>
 */
public class TournamentRunner {

    public static void main(String[] args) {
        // Check for --list-players before other args
        if (args.length >= 1 && "--list-players".equals(args[0])) {
            listPlayers();
            System.exit(0);
        }

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

            // Create discovery service
            PlayerDiscoveryService discoveryService = new PlayerDiscoveryService();
            discoveryService.initialize();

            runTournament(config, outputDir, engineLoader, discoveryService);
        } catch (Exception e) {
            System.err.println("Tournament failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Lists discovered players from the classpath.
     */
    private static void listPlayers() {
        PlayerDiscoveryService discoveryService = new PlayerDiscoveryService();
        discoveryService.initialize();

        List<DiscoveredPlayer> players = discoveryService.getDiscoveredPlayers();

        if (players.isEmpty()) {
            System.out.println("No players discovered on classpath.");
            return;
        }

        System.out.println("Discovered players:");
        System.out.println();
        for (DiscoveredPlayer p : players) {
            String desc = p.description().isEmpty() ? "" : " - " + p.description();
            System.out.printf("  %-30s %s%s%n", p.displayName(), p.className(), desc);
        }
        System.out.println();
        System.out.println("Use 'classpath:<class-name>' in --player arguments.");
    }

    static void runTournament(TournamentConfig config, Path outputDir, EngineLoader engineLoader,
                               PlayerDiscoveryService discoveryService) throws Exception {
        RoundFileWriter writer = new RoundFileWriter(outputDir);
        writer.writeTournamentMetadata(config);

        TableExecutor tableExecutor = new TableExecutor(engineLoader, discoveryService);
        ExecutorService threadPool = Executors.newFixedThreadPool(
            Math.min(8, Math.max(4, Runtime.getRuntime().availableProcessors()))
        );

        // Calculate games per player for balanced scheduling
        int gamesPerPlayer = config.gamesPerPlayer() > 0
            ? config.gamesPerPlayer()
            : RoundGenerator.recommendedGamesPerPlayer(config.players().size());
        int totalGamesPerRound = (config.players().size() * gamesPerPlayer) / 4;

        try {
            for (int round = 1; round <= config.rounds(); round++) {
                // Resume support: skip existing rounds
                if (writer.roundExists(round)) {
                    System.out.printf("Round %d already exists, skipping%n", round);
                    continue;
                }

                System.out.printf("Starting round %d/%d%n", round, config.rounds());

                List<Card.Type> kingdomCards = RoundGenerator.selectKingdomCards();

                // Generate balanced 4-player games
                List<List<PlayerConfig>> games = RoundGenerator.generateBalancedGames(
                    config.players(), gamesPerPlayer);

                // Run games in parallel batches
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

                java.util.Set<String> kingdomCardNames = kingdomCards.stream()
                    .map(Card.Type::name)
                    .collect(Collectors.toSet());

                RoundResult roundResult = new RoundResult(round, kingdomCardNames, matches);
                writer.writeRound(roundResult);

                System.out.printf("Round %d complete (%d games, %d per player)%n",
                    round, totalGamesPerRound, gamesPerPlayer);
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
        Integer gamesPerPlayer = null;
        int maxTurns = 100;
        List<PlayerConfig> players = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--name" -> name = args[++i];
                case "--rounds" -> rounds = Integer.parseInt(args[++i]);
                case "--games-per-player" -> gamesPerPlayer = Integer.parseInt(args[++i]);
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
                    players.add(new PlayerConfig(playerId, playerName, playerUrl, false));
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        if (name == null || rounds == null) {
            throw new IllegalArgumentException("Missing required arguments: --name, --rounds");
        }
        if (players.size() < 4) {
            throw new IllegalArgumentException("Need at least 4 players for balanced 4-player games");
        }

        // Auto-calculate gamesPerPlayer if not specified
        if (gamesPerPlayer == null) {
            gamesPerPlayer = RoundGenerator.recommendedGamesPerPlayer(players.size());
        }

        // Validate that N * gamesPerPlayer is divisible by 4
        if ((players.size() * gamesPerPlayer) % 4 != 0) {
            throw new IllegalArgumentException(
                "players * gamesPerPlayer must be divisible by 4. Got " +
                players.size() + " * " + gamesPerPlayer + " = " + (players.size() * gamesPerPlayer));
        }

        return new TournamentConfig(name, rounds, gamesPerPlayer, maxTurns, players);
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
        System.err.println("       java -jar atg-tournament-runner.jar --list-players");
        System.err.println();
        System.err.println("Arguments:");
        System.err.println("  <engine-jar>       Path to the JAR file containing the Engine implementation");
        System.err.println("  <engine-class>     Fully-qualified class name of the Engine implementation");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  --name <name>              Tournament name (required)");
        System.err.println("  --rounds <n>               Number of rounds to play (required)");
        System.err.println("  --games-per-player <n>     Games per player per round (auto-calculated if not specified)");
        System.err.println("  --output <dir>             Output directory (default: ./data)");
        System.err.println("  --max-turns <n>            Max turns per game (default: 100)");
        System.err.println("  --player <Name>=<url>      Add a player (at least 4 required)");
        System.err.println("  --list-players             List available players and exit");
        System.err.println();
        System.err.println("Player URLs:");
        System.err.println("  classpath:<class-name>           Player by class name (e.g., classpath:com.example.MyPlayer)");
        System.err.println("  https://...                      Network player URL");
        System.err.println();
        System.err.println("Use --list-players to see available classpath players.");
        System.err.println();
        System.err.println("Example:");
        System.err.println("  java -jar atg-tournament-runner.jar \\");
        System.err.println("    /path/to/engine.jar com.example.MyEngine \\");
        System.err.println("    --name test --rounds 3 --games-per-player 12 \\");
        System.err.println("    --player Alice=https://alice.example.com \\");
        System.err.println("    --player Bot1=classpath:com.example.BigMoneyPlayer \\");
        System.err.println("    --player Bot2=classpath:com.example.RandomPlayer \\");
        System.err.println("    --player Bot3=classpath:com.example.ActionPlayer");
    }
}
