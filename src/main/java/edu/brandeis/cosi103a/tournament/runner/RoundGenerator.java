package edu.brandeis.cosi103a.tournament.runner;

import edu.brandeis.cosi.atg.cards.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates round configurations: kingdom card selection and table assignments.
 */
public final class RoundGenerator {

    private static final List<Card.Type> ALL_ACTION_CARDS = List.of(
        Card.Type.REFACTOR, Card.Type.CODE_REVIEW, Card.Type.EVERGREEN_TEST,
        Card.Type.SPRINT_PLANNING, Card.Type.IPO, Card.Type.PARALLELIZATION,
        Card.Type.HACK, Card.Type.RANSOMWARE, Card.Type.MONITORING,
        Card.Type.BACKLOG, Card.Type.TECH_DEBT, Card.Type.DAILY_SCRUM,
        Card.Type.DEPLOYMENT_PIPELINE, Card.Type.UNIT_TEST, Card.Type.MERGE_CONFLICT
    );

    private RoundGenerator() {}

    /**
     * Selects 10 random kingdom cards from the 15 available action card types.
     */
    public static List<Card.Type> selectKingdomCards() {
        List<Card.Type> shuffled = new ArrayList<>(ALL_ACTION_CARDS);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, 10));
    }

    /**
     * Shuffles players into tables of 3-4 players each.
     * With N players: creates tables trying to keep sizes as even as possible,
     * each table having 3 or 4 players.
     */
    public static List<List<PlayerConfig>> shuffleIntoTables(List<PlayerConfig> players) {
        List<PlayerConfig> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        int n = shuffled.size();
        if (n < 3) {
            throw new IllegalArgumentException("Need at least 3 players for a tournament");
        }

        // Determine number of tables and sizes
        // We want tables of 3-4. Maximize tables of 4, remainder get 3.
        // numTables * 3 <= n <= numTables * 4
        // numTables = ceil(n / 4)
        int numTables = (n + 3) / 4; // ceiling division
        // But if numTables * 3 > n, we need fewer tables
        // Actually: we need numTables such that numTables * 3 <= n <= numTables * 4
        // numTables >= ceil(n/4), numTables <= floor(n/3)

        // Number of tables of 4: n - numTables * 3 tables get a 4th player
        int tablesOf4 = n - numTables * 3;

        List<List<PlayerConfig>> tables = new ArrayList<>();
        int idx = 0;
        for (int t = 0; t < numTables; t++) {
            int tableSize = (t < tablesOf4) ? 4 : 3;
            List<PlayerConfig> table = new ArrayList<>();
            for (int j = 0; j < tableSize && idx < n; j++) {
                table.add(shuffled.get(idx++));
            }
            tables.add(table);
        }
        return tables;
    }
}
