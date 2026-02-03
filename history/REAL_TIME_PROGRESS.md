# Real-Time Tournament Progress Tracking

## Context

Date: 2026-02-03
Repo: atg-tournament-runner (UI/viewer for tournament execution)
Related: Just merged fair scheduling feature to main

## Current State

### Update Granularity

**Current behavior:** Tournament progress updates happen at the **round level**
- UI receives updates when a full round completes (all games in that round finish)
- For a typical tournament: 15 rounds × 50 games/round = 750 total games
- Update frequency: every ~50 games (could be several minutes between updates)

**Code location:** `TournamentExecutionService.java:129-192`
```java
for (int round = 1; round <= config.rounds(); round++) {
    // Update status to RUNNING (round start)
    TournamentStatus runningStatus = TournamentStatus.running(...);
    sendWebSocketUpdate(tournamentId, runningStatus);

    // Generate games and run in parallel
    List<Future<MatchResult>> futures = new ArrayList<>();
    for (int g = 0; g < games.size(); g++) {
        futures.add(threadPool.submit(() -> tableExecutor.executeTable(...)));
    }

    // Wait for ALL games to complete
    for (Future<MatchResult> future : futures) {
        matches.add(future.get()); // BLOCKS until game finishes
    }

    // Write round results
    writer.writeRound(roundResult);

    // Update status AFTER all games complete
    TournamentStatus updatedStatus = TournamentStatus.running(...);
    sendWebSocketUpdate(tournamentId, updatedStatus);
}
```

### WebSocket Architecture

**Already implemented:**
- Spring WebSocket with STOMP messaging (`WebSocketConfig.java`)
- Status broadcasting: `messagingTemplate.convertAndSend("/topic/tournaments/" + tournamentId, status)`
- Frontend subscribes to tournament-specific topics
- Status object includes: state, currentRound, completedGames, totalGames, error

**Files:**
- `src/main/java/edu/brandeis/cosi103a/tournament/viewer/WebSocketConfig.java`
- `src/main/java/edu/brandeis/cosi103a/tournament/viewer/TournamentProgressController.java`
- `src/main/resources/static/app.js` (WebSocket client)

## Problem

**User experience issue:** During long tournaments, the UI appears frozen between rounds
- No indication that games are running
- Could be 2-5 minutes with no feedback
- User can't tell if system is working or hung

## Desired State

**Per-game granularity:** Update UI after each individual game completes
- Update frequency: every ~1 game (every few seconds)
- Shows continuous progress during a round
- Better UX: live progress bars, running leaderboards

**Improvement factor:** 50× more frequent updates (for tournaments with 50 games/round)

## Technical Approach

### Option 1: Callback per Game (Recommended)

Modify the parallel game execution to send updates as each game completes:

```java
// Current: Wait for all futures synchronously
for (Future<MatchResult> future : futures) {
    matches.add(future.get()); // Blocks
}

// Proposed: Process futures as they complete
CompletionService<MatchResult> completionService =
    new ExecutorCompletionService<>(threadPool);
for (Callable<MatchResult> task : tasks) {
    completionService.submit(task);
}

for (int i = 0; i < games.size(); i++) {
    MatchResult result = completionService.take().get(); // Get next completed game
    matches.add(result);
    completedGames++;

    // Send update after EACH game
    TournamentStatus status = TournamentStatus.running(
        tournamentId, round, config.rounds(), completedGames, totalGames
    );
    runningTournaments.put(tournamentId, status);
    sendWebSocketUpdate(tournamentId, status);
    if (progressListener != null) {
        progressListener.onProgress(status);
    }
}
```

**Pros:**
- Minimal changes to existing architecture
- Uses Java's CompletionService for clean async handling
- Already have WebSocket infrastructure

**Cons:**
- More WebSocket messages (but they're small, ~100 bytes each)
- Slightly more complex game collection logic

### Option 2: Streaming Progress from TableExecutor

Add a progress callback to `TableExecutor.executeTable()` that fires after each individual game within a match:

```java
// Current signature
public MatchResult executeTable(int tableNumber, List<PlayerConfig> players,
    List<Card.Type> kingdomCards, int gamesPerMatch, int maxTurns)

// Proposed signature
public MatchResult executeTable(int tableNumber, List<PlayerConfig> players,
    List<Card.Type> kingdomCards, int gamesPerMatch, int maxTurns,
    Consumer<GameResult> onGameComplete) // NEW callback

// Inside executeTable's game loop:
for (int g = 0; g < gamesPerMatch; g++) {
    GameResult result = runSingleGame(...);
    gameResults.add(result);
    if (onGameComplete != null) {
        onGameComplete.accept(result); // Fire callback
    }
}
```

**Pros:**
- Even finer granularity if gamesPerMatch > 1
- More extensible for future progress tracking needs

**Cons:**
- Requires changes to TableExecutor interface
- More complex callback chaining (TournamentExecutionService → TableExecutor → game loop)

## Implementation Checklist

- [ ] Choose approach (recommend Option 1 for simplicity)
- [ ] Modify game execution loop in `TournamentExecutionService.executeTournament()`
- [ ] Update `TournamentStatus` to include more granular info (current game within round?)
- [ ] Test with real tournament to verify update frequency
- [ ] Update frontend to handle higher-frequency updates (throttle/debounce if needed)
- [ ] Add unit tests for per-game progress callbacks
- [ ] Consider: Rate limiting WebSocket messages if needed (max 1/second?)

## Files to Modify

**Core changes:**
- `src/main/java/edu/brandeis/cosi103a/tournament/viewer/TournamentExecutionService.java:153-180`
  - Replace synchronous Future.get() loop with CompletionService
  - Add per-game WebSocket updates

**Possible changes:**
- `src/main/java/edu/brandeis/cosi103a/tournament/viewer/TournamentStatus.java`
  - Consider adding: `currentGameInRound`, `totalGamesInRound` fields
- `src/main/resources/static/app.js`
  - May need throttling if updates too frequent for UI refresh rate

## Testing Strategy

1. **Unit test:** Mock EngineLoader, verify callbacks fire after each game
2. **Integration test:** Run small tournament (4 players, 2 rounds, 2 games/player)
   - Verify: 4 status updates (2 rounds × 2 games/round)
   - Verify: Updates happen ~seconds apart, not minutes
3. **Manual test:** Watch UI during real tournament, confirm smooth progress

## Performance Considerations

**WebSocket message size:** ~100 bytes per update
- Current: 15 updates/tournament (1 per round)
- Proposed: 750 updates/tournament (1 per game)
- Bandwidth: 75 KB total (negligible)

**UI refresh rate:**
- Games complete every 2-5 seconds typically
- WebSocket messages every 2-5 seconds is fine (no throttling needed)
- If gamesPerMatch > 1, might want debouncing (max 1 update/second)

## Open Questions

1. Should we batch updates if multiple games complete within 100ms?
2. Do we need more granular status (game within round) or just completedGames count?
3. Should per-game updates persist to storage, or only send via WebSocket?

## References

- Fair scheduling PR: #2 (just merged)
- WebSocket setup: PR #1 (tournament viewer feature)
- CompletionService docs: https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionService.html
