# ATG Tournament Runner

Tournament runner for ATG (Automation: The Game). Run tournaments with your own engine JAR against network players and built-in bots.

## Overview

This tool allows you to run practice tournaments for ATG (Automation: The Game). You provide:
- Your own engine JAR file
- A mix of network players and built-in bots

The tool runs multiple rounds of games, shuffling players into tables of 3-4, and outputs results in JSON format.

## Tournament Format

### Structure

A tournament consists of multiple **rounds**. Each round:

1. **Kingdom Selection**: 10 action cards are randomly selected from the 15 available types
2. **Table Assignment**: Players are randomly shuffled into tables of 3-4 players
3. **Games**: Each table plays one game with the same kingdom and grouping
4. **Recording**: Results are written to a round file

Tables within a round run in parallel for efficiency.

### Scoring

Each game records the **final VP score** for every player. The output files include raw scores; TrueSkill ratings are computed live during tournament execution and displayed in the web UI.

Example game outcome:
```json
{
  "gameIndex": 0,
  "placements": [
    {"playerId": "alice", "score": 45},
    {"playerId": "bot1", "score": 38},
    {"playerId": "bot2", "score": 32}
  ]
}
```

To determine winners: sort by score descending. Ties are possible.

### Health Check

Before the tournament begins, every network player URL is probed with a POST to `/decide`. If a player's server is unreachable or times out, that player is excluded from the tournament entirely. Excluded players are reported in the initial tournament status. If fewer than 4 healthy players remain, the tournament fails to start.

### Time Budgets

When time budgets are enabled (`tournament.per-call-timeout-seconds` and `tournament.game-budget-seconds` both > 0):

- **Per-call timeout**: Each `/decide` HTTP request has a hard timeout. If the player's server doesn't respond in time, that single decision is forfeited (a passive default decision is made instead).
- **Per-game budget**: Cumulative decision time is tracked across all `/decide` calls in a game. If the budget is exceeded, the player makes passive default decisions for the remainder of that game. The player retains their actual VP score — they are not forced to last place.

### Error Handling

Individual player timeouts and exceptions are handled gracefully — only the affected player's decision is forfeited, and the game continues normally. If the game engine itself crashes (not a player issue), all players in that game receive a score of 0.

## Prerequisites

- **Docker** installed and running
- **Your engine JAR** - a packaged JAR containing your `Engine` implementation
- **Network player deployed** (optional) - if testing against your own player, it must be accessible via HTTP

## Quick Start

Run tournaments with the web interface:

```bash
docker run --rm \
  -p 8081:8081 \
  -v $(pwd)/my-engine.jar:/app/engine.jar \
  -v $(pwd)/data:/app/data \
  -e TOURNAMENT_ENGINE_JAR=/app/engine.jar \
  -e TOURNAMENT_ENGINE_CLASS=com.example.MyEngine \
  -e TOURNAMENT_PER_CALL_TIMEOUT_SECONDS=10 \
  -e TOURNAMENT_GAME_BUDGET_SECONDS=120 \
  ghcr.io/brandeis-cosi-103a/atg-tournament-runner
```

Then open **http://localhost:8081** in your browser to:
1. Configure your tournament (name, rounds, games per player)
2. Add players (network URLs or built-in bots from the dropdown)
3. Click "Run Tournament" and track progress with live TrueSkill ratings
4. View animated results playback automatically when complete

### Built-in Bots

Three built-in bot players are always available in the web UI dropdown:

| Bot | Strategy |
|-----|----------|
| **NaiveBigMoneyPlayer** | Buys the highest-cost money card it can afford, buys Framework when possible |
| **ActionHeavyPlayer** | Prioritizes action cards over money, plays all available actions |
| **RandomPlayer** | Completely random legal decisions — useful as a baseline |

### Adding Your Own Player JARs

To make additional `Player` implementations available as bots, add their JARs to the classpath. Any class implementing the `Player` interface with a zero-arg or `String` constructor is automatically discovered and appears in the web UI dropdown.

```bash
docker run --rm \
  -p 8081:8081 \
  -v $(pwd)/my-engine.jar:/app/engine.jar \
  -v $(pwd)/my-player.jar:/app/player.jar \
  -v $(pwd)/data:/app/data \
  -e TOURNAMENT_ENGINE_JAR=/app/engine.jar \
  -e TOURNAMENT_ENGINE_CLASS=com.example.MyEngine \
  -e CLASSPATH=/app/runner.jar:/app/player.jar \
  ghcr.io/brandeis-cosi-103a/atg-tournament-runner
```

### Network Players

To include a network player, select "URL (Network Player)" from the dropdown and enter the player's base URL (e.g., `https://my-player.azurewebsites.net`). The server must expose `/decide` and `/log-event` endpoints.

## Output Format

Results are written to the output directory:

```
<output-dir>/<tournament-name>/
├── tournament.json    # Tournament metadata and player list
├── round-01.json      # Results from round 1
├── round-02.json      # Results from round 2
└── ...
```

### tournament.json

```json
{
  "name": "practice",
  "config": {
    "rounds": 3,
    "gamesPerTable": 10,
    "maxTurns": 100
  },
  "players": [
    {"id": "alice", "name": "Alice", "url": "https://..."},
    {"id": "bot1", "name": "Bot1", "url": "naive-money"}
  ]
}
```

### round-NN.json

```json
{
  "roundNumber": 1,
  "kingdomCards": ["REFACTOR", "CODE_REVIEW", "HACK", ...],
  "matches": [
    {
      "tableNumber": 1,
      "playerIds": ["alice", "bot1", "bot2"],
      "outcomes": [
        {
          "gameIndex": 0,
          "placements": [
            {"playerId": "alice", "score": 45},
            {"playerId": "bot1", "score": 38},
            {"playerId": "bot2", "score": 32}
          ]
        },
        ...
      ]
    }
  ]
}
```

### Resume Support

If a tournament is interrupted, re-running with the same tournament name will skip rounds that already have output files and continue from where it left off.

## Bot Strategy Details

### NaiveBigMoneyPlayer

- Buys the highest-cost money card it can afford
- Buys Framework cards when possible
- Plays some action cards that provide money bonuses
- No complex decision-making or expected value calculations

### ActionHeavyPlayer

- Prioritizes buying action cards over money
- Plays all available action cards
- Falls back to money and Framework cards when no actions available
- Doesn't optimize for card synergies

### RandomPlayer

- Chooses uniformly at random from available options
- Useful as a baseline for comparison

## Troubleshooting

### Connection errors to network player

- Ensure your player is deployed and accessible from the Docker container
- If running locally, use your machine's IP address instead of `localhost`
- Check that the `/decide` and `/log-event` endpoints are responding

### Timeouts

- Per-call and game budget timeouts are configured via `tournament.per-call-timeout-seconds` and `tournament.game-budget-seconds` (both must be > 0 to enable)
- Event logging (`/log-event`) has a separate 5-second timeout that does not count toward the game budget
- If a player is forfeiting every decision, check that their server responds within the per-call timeout
- If games are timing out, check your engine's turn limit handling

### Engine class not found

- Ensure your JAR is a "fat JAR" or "shaded JAR" with all dependencies included
- Verify the class name is fully-qualified (e.g., `com.example.MyEngine`)
- Check that your engine implements the `Engine` interface from atg-api

### Docker volume mounting issues

- Ensure paths are absolute or use `$(pwd)` for the current directory
- On Windows, you may need to adjust the volume mount syntax

## Viewing Results

The web UI provides automatic progress tracking and results playback:
- **Live progress**: Real-time updates showing current round, games completed, and TrueSkill ratings
- **Auto-redirect**: Automatically opens the animated results viewer when the tournament finishes
- **Animated leaderboard**: Watch ratings evolve game-by-game
- **Playback controls**: Play/pause, rewind, fast-forward (1x to 50x speed)
- **Timeline scrubber**: Click anywhere to jump to a specific game
- **Round markers**: Visual indicators of round boundaries
- **Podium celebration**: Final standings at tournament end

Previously completed tournaments can also be viewed by selecting them from the main page.

## Important Notes

- **You provide your own engine** - this tool does NOT include a reference engine
- **Your network player must be accessible** - deployed and responding to HTTP requests
- **Same tool used for official tournament** - the instructor uses this same tool with the reference engine for grading
