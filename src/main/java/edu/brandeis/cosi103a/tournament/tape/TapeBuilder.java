package edu.brandeis.cosi103a.tournament.tape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.gesundkrank.jskills.GameInfo;
import de.gesundkrank.jskills.Rating;
import edu.brandeis.cosi103a.tournament.runner.Placement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * CLI tool that reads raw round files and produces a playback tape with running TrueSkill ratings.
 */
public final class TapeBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private TapeBuilder() {}

    /**
     * Build tape.json from tournament directory.
     *
     * @param tournamentDir directory containing tournament.json and round-*.json files
     * @param gameInfo      TrueSkill parameters
     */
    public static void buildTape(Path tournamentDir, GameInfo gameInfo) {
        Path tournamentFile = tournamentDir.resolve("tournament.json");
        if (!Files.exists(tournamentFile)) {
            throw new IllegalArgumentException("tournament.json not found in " + tournamentDir);
        }

        try {
            JsonNode tournament = MAPPER.readTree(tournamentFile.toFile());
            JsonNode playersNode = tournament.get("players");

            // Build player list and initialize tracker
            List<ObjectNode> players = new ArrayList<>();
            List<String> playerIds = new ArrayList<>();
            for (JsonNode p : playersNode) {
                String id = p.get("id").asText();
                String name = p.get("name").asText();
                ObjectNode playerObj = MAPPER.createObjectNode();
                playerObj.put("id", id);
                playerObj.put("name", name);
                players.add(playerObj);
                playerIds.add(id);
            }
            TrueSkillTracker tracker = new TrueSkillTracker(playerIds, gameInfo);

            // Track card frequencies per player (card type -> count)
            Map<String, Map<String, Integer>> playerCardCounts = new HashMap<>();
            for (String playerId : playerIds) {
                playerCardCounts.put(playerId, new HashMap<>());
            }

            // Find and sort round files
            List<Path> roundFiles;
            try (Stream<Path> paths = Files.list(tournamentDir)) {
                roundFiles = paths
                        .filter(p -> p.getFileName().toString().matches("round-\\d+\\.json"))
                        .sorted(Comparator.comparing(p -> {
                            String name = p.getFileName().toString();
                            return Integer.parseInt(name.replaceAll("\\D+", ""));
                        }))
                        .toList();
            }

            // Process events — one event per game index across all tables
            // (game 0 on all tables, then game 1 on all tables, etc.)
            ArrayNode events = MAPPER.createArrayNode();
            int seq = 0;

            for (Path roundFile : roundFiles) {
                JsonNode round = MAPPER.readTree(roundFile.toFile());
                int roundNumber = round.get("roundNumber").asInt();
                JsonNode kingdomCardsNode = round.get("kingdomCards");

                List<String> kingdomCards = new ArrayList<>();
                for (JsonNode kc : kingdomCardsNode) {
                    kingdomCards.add(kc.asText());
                }

                // Index games by gameIndex -> list of placements (one per table)
                Map<Integer, List<List<Placement>>> gamesByIndex = new HashMap<>();
                int tableCount = 0;
                for (JsonNode match : round.get("matches")) {
                    tableCount++;
                    for (JsonNode outcome : match.get("outcomes")) {
                        int gameIndex = outcome.get("gameIndex").asInt();
                        List<Placement> placements = new ArrayList<>();
                        for (JsonNode pl : outcome.get("placements")) {
                            String playerId = pl.get("playerId").asText();
                            int score = pl.get("score").asInt();
                            List<String> deck = new ArrayList<>();
                            JsonNode deckNode = pl.get("deck");
                            if (deckNode != null && deckNode.isArray()) {
                                for (JsonNode cardType : deckNode) {
                                    String cardName = cardType.asText();
                                    deck.add(cardName);
                                    // Aggregate card counts
                                    Map<String, Integer> counts = playerCardCounts.get(playerId);
                                    if (counts != null) {
                                        counts.merge(cardName, 1, Integer::sum);
                                    }
                                }
                            }
                            placements.add(new Placement(playerId, score, deck));
                        }
                        gamesByIndex.computeIfAbsent(gameIndex, k -> new ArrayList<>())
                                .add(placements);
                    }
                }

                // Step through game indices in order
                List<Integer> sortedIndices = new ArrayList<>(gamesByIndex.keySet());
                sortedIndices.sort(Comparator.naturalOrder());
                int gamesInRound = sortedIndices.size();

                for (int gameIndex : sortedIndices) {
                    List<List<Placement>> tablesForGame = gamesByIndex.get(gameIndex);

                    // Emit one event per table (per game)
                    int tableNum = 0;
                    for (List<Placement> placements : tablesForGame) {
                        tableNum++;

                        // Apply TrueSkill for this single game
                        tracker.processGame(placements);

                        // Build event
                        ObjectNode event = MAPPER.createObjectNode();
                        event.put("seq", seq++);
                        event.put("round", roundNumber);
                        event.put("game", gameIndex);
                        event.put("gamesInRound", gamesInRound);
                        event.put("table", tableNum);
                        event.put("tables", tableCount);

                        ArrayNode kcArray = MAPPER.createArrayNode();
                        kingdomCards.forEach(kcArray::add);
                        event.set("kingdomCards", kcArray);

                        // Include placements from this table only
                        ArrayNode placementsArray = MAPPER.createArrayNode();
                        for (Placement p : placements) {
                            ObjectNode pn = MAPPER.createObjectNode();
                            pn.put("id", p.playerId());
                            pn.put("score", p.score());
                            placementsArray.add(pn);
                        }
                        event.set("placements", placementsArray);

                        // Emit ratings: conservative (mu - 3*sigma) for display, plus raw mu/sigma for auditing
                        Map<String, Rating> ratings = tracker.getCurrentRatings();
                        ObjectNode ratingsNode = MAPPER.createObjectNode();
                        ObjectNode muNode = MAPPER.createObjectNode();
                        ObjectNode sigmaNode = MAPPER.createObjectNode();
                        for (var entry : ratings.entrySet()) {
                            String playerId = entry.getKey();
                            Rating rating = entry.getValue();
                            double display = tracker.getConservativeRating(playerId);
                            ratingsNode.put(playerId, Math.round(display * 10.0) / 10.0);
                            muNode.put(playerId, Math.round(rating.getMean() * 100.0) / 100.0);
                            sigmaNode.put(playerId, Math.round(rating.getStandardDeviation() * 100.0) / 100.0);
                        }
                        event.set("ratings", ratingsNode);
                        event.set("mu", muNode);
                        event.set("sigma", sigmaNode);

                        // Emit cumulative placement points
                        Map<String, Integer> points = tracker.getCurrentPoints();
                        ObjectNode pointsNode = MAPPER.createObjectNode();
                        for (var entry : points.entrySet()) {
                            pointsNode.put(entry.getKey(), entry.getValue());
                        }
                        event.set("points", pointsNode);

                        events.add(event);
                    }
                }
            }

            // Build tape
            ObjectNode tape = MAPPER.createObjectNode();

            ArrayNode playersArray = MAPPER.createArrayNode();
            players.forEach(playersArray::add);
            tape.set("players", playersArray);

            ObjectNode scoring = MAPPER.createObjectNode();
            scoring.put("model", "trueskill");
            Rating defaultRating = gameInfo.getDefaultRating();
            scoring.put("initial",
                    Math.round(TrueSkillRatingCalculator.conservativeRating(defaultRating) * 10.0) / 10.0);
            tape.set("scoring", scoring);

            tape.set("events", events);

            // Add aggregated deck stats per player
            ObjectNode deckStats = MAPPER.createObjectNode();
            for (var entry : playerCardCounts.entrySet()) {
                String playerId = entry.getKey();
                Map<String, Integer> cardCounts = entry.getValue();
                if (!cardCounts.isEmpty()) {
                    ObjectNode playerCards = MAPPER.createObjectNode();
                    for (var cardEntry : cardCounts.entrySet()) {
                        playerCards.put(cardEntry.getKey(), cardEntry.getValue());
                    }
                    deckStats.set(playerId, playerCards);
                }
            }
            tape.set("deckStats", deckStats);

            MAPPER.writeValue(tournamentDir.resolve("tape.json").toFile(), tape);

        } catch (IOException e) {
            throw new RuntimeException("Failed to build tape", e);
        }
    }

    public static void main(String[] args) {
        String tournamentDir = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--tournament" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--tournament requires a path argument");
                    }
                    tournamentDir = args[++i];
                }
                // Keep --k-factor for backwards compat but ignore it
                case "--k-factor" -> i++;
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        if (tournamentDir == null) {
            throw new IllegalArgumentException("--tournament argument is required");
        }

        TrueSkillRatingCalculator.resetConvergenceFailures();
        buildTape(Path.of(tournamentDir), GameInfo.getDefaultGameInfo());

        int failures = TrueSkillRatingCalculator.getConvergenceFailures();
        if (failures > 0) {
            System.out.println("tape.json written to " + tournamentDir +
                " (" + failures + " TrueSkill convergence failures, ratings may be approximate)");
        } else {
            System.out.println("tape.json written to " + tournamentDir);
        }
    }
}
