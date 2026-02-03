package edu.brandeis.cosi103a.tournament.runner;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Outcome of a single game at a table.
 */
public record GameOutcome(
    @JsonProperty("gameIndex") int gameIndex,
    @JsonProperty("placements") List<Placement> placements
) {}
