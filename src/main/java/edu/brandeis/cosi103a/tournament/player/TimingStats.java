package edu.brandeis.cosi103a.tournament.player;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimingStats(
    long totalDecisionTimeMs,
    int decisionCount,
    int timeoutCount,
    boolean forfeited,
    Integer decisionAtForfeit,  // null if not forfeited
    String forfeitReason,       // "TIME_BUDGET" | "EXCEPTION" | null when player ran cleanly
    String lastExceptionMessage // class+message of the most recent caught exception, null if none
) {}
