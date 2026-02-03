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
 * A naive "action-heavy" bot for student practice.
 *
 * Strategy:
 * - Buy phase: prioritize action cards over money cards
 * - Action phase: play all action cards available
 * - Money phase: play all money cards
 *
 * This bot is deliberately simple and uses basic heuristics.
 * It prioritizes action cards but doesn't optimize their synergies.
 */
public class ActionHeavyPlayer implements Player {
    private final String name;

    public ActionHeavyPlayer() {
        this("ActionHeavy");
    }

    public ActionHeavyPlayer(String name) {
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
            case ACTION -> PlayerHelpers.playAllActionCards(options);
            case MONEY -> PlayerHelpers.playAllMoneyCards(options);
            case BUY -> handleBuyPhase(options);
            case GAIN -> handleGainPhase(options);
            default -> PlayerHelpers.findEndPhaseDecision(options);
        };
    }

    /**
     * Buy phase: action cards first (highest cost), then money, then Framework.
     */
    private Decision handleBuyPhase(ImmutableList<Decision> options) {
        BuyDecision bestActionBuy = null;
        int bestActionCost = -1;
        BuyDecision bestMoneyBuy = null;
        int bestMoneyCost = -1;
        BuyDecision frameworkBuy = null;

        for (Decision option : options) {
            if (option instanceof BuyDecision buyDecision) {
                Card.Type cardType = buyDecision.cardType();

                if (cardType == Card.Type.FRAMEWORK) {
                    frameworkBuy = buyDecision;
                } else if (cardType.category() == Card.Type.Category.ACTION) {
                    if (cardType.cost() > bestActionCost) {
                        bestActionCost = cardType.cost();
                        bestActionBuy = buyDecision;
                    }
                } else if (cardType.category() == Card.Type.Category.MONEY) {
                    if (cardType.cost() > bestMoneyCost) {
                        bestMoneyCost = cardType.cost();
                        bestMoneyBuy = buyDecision;
                    }
                }
            }
        }

        // Priority: action cards > money cards > Framework > end
        if (bestActionBuy != null) {
            return bestActionBuy;
        }
        if (bestMoneyBuy != null) {
            return bestMoneyBuy;
        }
        if (frameworkBuy != null) {
            return frameworkBuy;
        }
        return PlayerHelpers.findEndPhaseDecision(options);
    }

    /**
     * Gain phase: prefer action cards, then highest cost.
     */
    private Decision handleGainPhase(ImmutableList<Decision> options) {
        GainCardDecision bestActionGain = null;
        int bestActionCost = -1;
        GainCardDecision bestOtherGain = null;
        int bestOtherCost = -1;

        for (Decision option : options) {
            if (option instanceof GainCardDecision gainDecision) {
                Card.Type cardType = gainDecision.cardType();
                int cost = cardType.cost();

                if (cardType.category() == Card.Type.Category.ACTION) {
                    if (cost > bestActionCost) {
                        bestActionCost = cost;
                        bestActionGain = gainDecision;
                    }
                } else {
                    if (cost > bestOtherCost) {
                        bestOtherCost = cost;
                        bestOtherGain = gainDecision;
                    }
                }
            }
        }

        if (bestActionGain != null) {
            return bestActionGain;
        }
        if (bestOtherGain != null) {
            return bestOtherGain;
        }
        return options.get(0);
    }
}
