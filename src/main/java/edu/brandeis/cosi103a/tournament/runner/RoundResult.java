package edu.brandeis.cosi103a.tournament.runner;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

/**
 * Result of an entire round of tournament play.
 */
public record RoundResult(
    @JsonProperty("roundNumber") int roundNumber,
    @JsonProperty("kingdomCards") Set<String> kingdomCards,
    @JsonProperty("matches") List<MatchResult> matches
) {}
