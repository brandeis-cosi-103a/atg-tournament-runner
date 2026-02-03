package edu.brandeis.cosi103a.tournament.player;

import com.google.common.collect.ImmutableList;
import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi.atg.decisions.*;

/**
 * Static utility methods shared across player implementations.
 */
public final class PlayerHelpers {

    private PlayerHelpers() {
        // Utility class
    }

    /**
     * Money phase: play all money cards, then end phase.
     */
    public static Decision playAllMoneyCards(ImmutableList<Decision> options) {
        for (Decision option : options) {
            if (option instanceof PlayCardDecision playDecision) {
                Card card = playDecision.card();
                if (card.category() == Card.Type.Category.MONEY) {
                    return option;
                }
            }
        }
        return findEndPhaseDecision(options);
    }

    /**
     * Action phase: play all action cards, then end phase.
     */
    public static Decision playAllActionCards(ImmutableList<Decision> options) {
        for (Decision option : options) {
            if (option instanceof PlayCardDecision playDecision) {
                Card card = playDecision.card();
                if (card.category() == Card.Type.Category.ACTION) {
                    return option;
                }
            }
        }
        return findEndPhaseDecision(options);
    }

    /**
     * Gain phase: gain the highest-cost card available.
     */
    public static Decision gainHighestCostCard(ImmutableList<Decision> options) {
        GainCardDecision bestGain = null;
        int bestValue = Integer.MIN_VALUE;

        for (Decision option : options) {
            if (option instanceof GainCardDecision gainDecision) {
                int value = gainDecision.cardType().cost();
                if (value > bestValue) {
                    bestValue = value;
                    bestGain = gainDecision;
                }
            }
        }

        if (bestGain != null) {
            return bestGain;
        }

        return options.get(0);
    }

    /**
     * Helper to find EndPhaseDecision in options.
     */
    public static Decision findEndPhaseDecision(ImmutableList<Decision> options) {
        for (Decision option : options) {
            if (option instanceof EndPhaseDecision) {
                return option;
            }
        }
        return options.get(0);
    }
}
