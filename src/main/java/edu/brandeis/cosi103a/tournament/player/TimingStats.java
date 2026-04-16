package edu.brandeis.cosi103a.tournament.player;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimingStats(
    long totalDecisionTimeMs,
    int decisionCount,
    int timeoutCount,
    boolean forfeited,
    Integer decisionAtForfeit  // null if not forfeited
) {}
