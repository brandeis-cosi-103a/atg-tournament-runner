package edu.brandeis.cosi103a.tournament.network.dto;

import edu.brandeis.cosi.atg.decisions.Decision;

/**
 * Response object for the /decide endpoint.
 * Represents a decision chosen by the player.
 */
public class DecisionResponse {
    private Decision decision;

    /**
     * No-args constructor for Jackson serialization.
     */
    public DecisionResponse() {
    }

    /**
     * Constructor with decision.
     */
    public DecisionResponse(Decision decision) {
        this.decision = decision;
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }
}
