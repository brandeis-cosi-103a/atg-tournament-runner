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
3. **Games**: Each table plays N games (specified by `--games-per-table`) with the same kingdom and grouping
4. **Recording**: Results are written to a round file

Tables within a round run in parallel for efficiency.

### Scoring

Each game records the **final VP score** for every player. The output includes raw scores only - no rankings or ratings are computed by this tool.

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

### Error Handling

If a game fails (engine error, player violation, timeout), all players in that game receive a score of 0 for that game.

## Prerequisites

- **Docker** installed and running
- **Your engine JAR** - a packaged JAR containing your `Engine` implementation
- **Network player deployed** (optional) - if testing against your own player, it must be accessible via HTTP

## Quick Start (Web UI - Recommended)

Run tournaments with an easy-to-use web interface:

```bash
docker run --rm \
  -p 8080:8080 \
  -v $(pwd)/my-engine.jar:/app/engine.jar \
  -v $(pwd)/data:/app/data \
  -e TOURNAMENT_ENGINE_JAR=/app/engine.jar \
  -e TOURNAMENT_ENGINE_CLASS=com.example.MyEngine \
  ghcr.io/brandeis-cosi-103a/atg-tournament-runner
```

Then open **http://localhost:8080** in your browser to:
1. Configure your tournament (name, rounds, games per table)
2. Add players (network URLs or built-in bots)
3. Click "Run Tournament" and watch live progress
4. View animated results automatically when complete

**No complex CLI commands needed!**

## Quick Start (CLI - Advanced)

For automated scripts or CI/CD pipelines, use the command-line interface:

```bash
docker run --rm \
  -v $(pwd):/jars \
  -v $(pwd)/results:/data \
  ghcr.io/brandeis-cosi-103a/atg-tournament-runner \
  /jars/my-engine.jar com.example.MyEngine \
  --name practice --rounds 3 --games-per-table 10 \
  --output /data \
  --player Student=https://my-player.azurewebsites.net \
  --player Bot1=naive-money \
  --player Bot2=action-heavy \
  --player Bot3=random
```

## CLI Usage (Advanced)

For automation, scripting, or CI/CD integration, use the command-line interface:

```
docker run --rm \
  -v <path-to-jars>:/jars \
  -v <output-dir>:/data \
  ghcr.io/brandeis-cosi-103a/atg-tournament-runner \
  <engine-jar> <engine-class> [options]
```

### Arguments

| Argument | Description |
|----------|-------------|
| `<engine-jar>` | Path to your engine JAR file (inside the container) |
| `<engine-class>` | Fully-qualified class name of your Engine implementation |

### Options

| Option | Description |
|--------|-------------|
| `--name <name>` | Tournament name (required) |
| `--rounds <n>` | Number of rounds to play (required) |
| `--games-per-table <n>` | Games per table per round (required) |
| `--output <dir>` | Output directory (default: ./data) |
| `--max-turns <n>` | Max turns per game (default: 100) |
| `--player <Name>=<url>` | Add a player (at least 3 required) |

### Player Types

| Value | Description |
|-------|-------------|
| `https://...` | Network player URL |
| `naive-money` | Built-in naive "big money" strategy bot |
| `action-heavy` | Built-in action-focused strategy bot |
| `random` | Built-in random decision bot |

## CLI Usage Examples

### Testing against all bots

```bash
docker run --rm \
  -v $(pwd):/jars \
  -v $(pwd)/results:/data \
  ghcr.io/brandeis-cosi-103a/atg-tournament-runner \
  /jars/my-engine.jar com.example.MyEngine \
  --name bot-test --rounds 5 --games-per-table 20 \
  --output /data \
  --player NaiveMoney=naive-money \
  --player ActionHeavy=action-heavy \
  --player Random=random
```

### Larger tournament with network player

```bash
docker run --rm \
  -v $(pwd):/jars \
  -v $(pwd)/results:/data \
  ghcr.io/brandeis-cosi-103a/atg-tournament-runner \
  /jars/my-engine.jar com.example.MyEngine \
  --name full-test --rounds 15 --games-per-table 50 \
  --output /data \
  --player MyPlayer=https://my-player.azurewebsites.net \
  --player Bot1=naive-money \
  --player Bot2=action-heavy \
  --player Bot3=random
```

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

If a tournament is interrupted, re-running with the same `--name` will skip rounds that already have output files and continue from where it left off.

## Bot Strategies

### naive-money

A simple money-focused strategy:
- Buys the highest-cost money card it can afford
- Buys Framework cards when possible
- Plays some action cards that provide money bonuses
- No complex decision-making or expected value calculations

### action-heavy

An action card enthusiast:
- Prioritizes buying action cards over money
- Plays all available action cards
- Falls back to money and Framework cards when no actions available
- Doesn't optimize for card synergies

### random

Completely random legal decisions:
- Chooses uniformly at random from available options
- Useful as a baseline for comparison

## Troubleshooting

### Connection errors to network player

- Ensure your player is deployed and accessible from the Docker container
- If running locally, use your machine's IP address instead of `localhost`
- Check that the `/decide` and `/log-event` endpoints are responding

### Timeouts

- Network players have a 5-second timeout for event logging
- If games are timing out, check your engine's turn limit handling
- Consider using `--max-turns` to limit game length

### Engine class not found

- Ensure your JAR is a "fat JAR" or "shaded JAR" with all dependencies included
- Verify the class name is fully-qualified (e.g., `com.example.MyEngine`)
- Check that your engine implements the `Engine` interface from atg-api

### Docker volume mounting issues

- Ensure paths are absolute or use `$(pwd)` for the current directory
- On Windows, you may need to adjust the volume mount syntax

## Viewing Results

### Web UI (Automatic)

When using the web UI, results are automatically displayed after tournament completion:
- **Live progress**: Watch rounds complete in real-time via WebSocket updates
- **Auto-redirect**: Automatically opens the animated viewer when finished
- **TrueSkill ratings**: Tape file with ratings is generated automatically

Simply run your tournament via the web UI and the results will appear automatically!

### CLI (Manual)

If you ran a tournament via CLI, you need to manually build the tape and start the viewer:

**1. Build the tape file:**
```bash
java -cp target/atg-tournament-runner-*-shaded.jar \
  edu.brandeis.cosi103a.tournament.tape.TapeBuilder \
  --tournament ./data/my-tournament
```

This generates `tape.json` with TrueSkill ratings.

**2. Start the viewer:**
```bash
# Using Docker
docker run --rm \
  -v $(pwd)/data:/app/data \
  -p 8080:8080 \
  ghcr.io/brandeis-cosi-103a/atg-tournament-runner

# Using Java directly
java -jar target/atg-tournament-runner-*-shaded.jar
```

Then open **http://localhost:8080** and select your tournament.

### Viewer Features

- **Animated leaderboard**: Watch ratings evolve game-by-game
- **Playback controls**: Play/pause, rewind, fast-forward (1x to 50x speed)
- **Timeline scrubber**: Click anywhere to jump to a specific game
- **Round markers**: Visual indicators of round boundaries
- **Podium celebration**: Final standings at tournament end

## Important Notes

- **You provide your own engine** - this tool does NOT include a reference engine
- **Your network player must be accessible** - deployed and responding to HTTP requests
- **Same tool used for official tournament** - the instructor uses this same tool with the reference engine for grading
