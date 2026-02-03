package edu.brandeis.cosi103a.tournament.network.dto;

import com.google.common.collect.ImmutableList;
import edu.brandeis.cosi.atg.decisions.Decision;
import edu.brandeis.cosi.atg.event.Event;
import edu.brandeis.cosi.atg.state.GameState;

import java.util.Optional;

/**
 * Request object for the /decide endpoint.
 * Represents a request for a player to choose a decision.
 */
public class DecisionRequest {
    private GameState state;
    private ImmutableList<Decision> options;
    private Optional<Event> reason;
    private String playerUuid;

    /**
     * No-args constructor for Jackson deserialization.
     */
    public DecisionRequest() {
    }

    /**
     * Full constructor.
     */
    public DecisionRequest(GameState state, ImmutableList<Decision> options, Optional<Event> reason, String playerUuid) {
        this.state = state;
        this.options = options;
        this.reason = reason;
        this.playerUuid = playerUuid;
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public ImmutableList<Decision> getOptions() {
        return options;
    }

    public void setOptions(ImmutableList<Decision> options) {
        this.options = options;
    }

    public Optional<Event> getReason() {
        return reason;
    }

    public void setReason(Optional<Event> reason) {
        this.reason = reason;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }
}
