package edu.brandeis.cosi103a.tournament.player;

import java.util.Optional;

import com.google.common.collect.ImmutableList;
import edu.brandeis.cosi.atg.cards.Card;
import edu.brandeis.cosi.atg.decisions.*;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.event.GameObserver;
import edu.brandeis.cosi.atg.player.Player;
import edu.brandeis.cosi.atg.state.GameState;

/**
 * A naive "big money" bot for student practice.
 *
 * Strategy:
 * - Buy phase: buy the highest-cost money card affordable, then Framework
 * - Action phase: play action cards that provide +money bonus
 * - Money phase: play all money cards
 *
 * This bot is deliberately simple and does NOT use expected value calculations
 * or sophisticated heuristics.
 */
public class NaiveBigMoneyPlayer implements Player {
    private final String name;

    public NaiveBigMoneyPlayer() {
        this("NaiveMoney");
    }

    public NaiveBigMoneyPlayer(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Optional<GameObserver> getObserver() {
        return Optional.empty();
    }

    @Override
    public Decision makeDecision(GameState state, ImmutableList<Decision> options, Optional<Event> event) {
        return switch (state.phase()) {
            case ACTION -> handleActionPhase(options);
            case MONEY -> PlayerHelpers.playAllMoneyCards(options);
            case BUY -> handleBuyPhase(options);
            case GAIN -> PlayerHelpers.gainHighestCostCard(options);
            default -> PlayerHelpers.findEndPhaseDecision(options);
        };
    }

    /**
     * Action phase: play action cards that give +money, then end.
     */
    private Decision handleActionPhase(ImmutableList<Decision> options) {
        // Play actions that give money bonus (simple heuristic)
        for (Decision option : options) {
            if (option instanceof PlayCardDecision playDecision) {
                Card card = playDecision.card();
                // Simple heuristic: play low-cost action cards that typically give money
                if (card.type() == Card.Type.UNIT_TEST ||
                    card.type() == Card.Type.CODE_REVIEW ||
                    card.type() == Card.Type.DAILY_SCRUM) {
                    return option;
                }
            }
        }
        return PlayerHelpers.findEndPhaseDecision(options);
    }

    /**
     * Buy phase: highest-cost money card first, then Framework.
     */
    private Decision handleBuyPhase(ImmutableList<Decision> options) {
        BuyDecision bestMoneyBuy = null;
        int bestMoneyCost = -1;
        BuyDecision frameworkBuy = null;

        for (Decision option : options) {
            if (option instanceof BuyDecision buyDecision) {
                Card.Type cardType = buyDecision.cardType();

                if (cardType == Card.Type.FRAMEWORK) {
                    frameworkBuy = buyDecision;
                }

                if (cardType.category() == Card.Type.Category.MONEY) {
                    if (cardType.cost() > bestMoneyCost) {
                        bestMoneyCost = cardType.cost();
                        bestMoneyBuy = buyDecision;
                    }
                }
            }
        }

        // Priority: highest money > Framework > end
        if (bestMoneyBuy != null) {
            return bestMoneyBuy;
        }
        if (frameworkBuy != null) {
            return frameworkBuy;
        }
        return PlayerHelpers.findEndPhaseDecision(options);
    }
}
