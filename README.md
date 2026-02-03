# ATG Tournament Runner

Tournament runner for ATG (Automation: The Game). Run tournaments with your own engine JAR against network players and built-in bots.

## Overview

This tool allows you to run practice tournaments for ATG (Automation: The Game). You provide:
- Your own engine JAR file
- A mix of network players and built-in bots

The tool runs multiple rounds of games, shuffling players into tables of 3-4, and outputs results in JSON format.

## Prerequisites

- **Docker** installed and running
- **Your engine JAR** - a packaged JAR containing your `Engine` implementation
- **Network player deployed** (optional) - if testing against your own player, it must be accessible via HTTP

## Quick Start

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
  --player Bot2=action-lover \
  --player Bot3=random
```

## Usage

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
| `action-lover` | Built-in action-focused strategy bot |
| `random` | Built-in random decision bot |

## Usage Examples

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
  --player ActionLover=action-lover \
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
  --player Bot2=action-lover \
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

Each round file contains:
- Kingdom cards used for that round
- Table assignments
- Game outcomes with player placements and scores

## Bot Strategies

### naive-money

A simple money-focused strategy:
- Buys the highest-cost money card it can afford
- Buys Framework cards when possible
- Plays some action cards that provide money bonuses
- No complex decision-making or expected value calculations

### action-lover

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

## Important Notes

- **You provide your own engine** - this tool does NOT include a reference engine
- **Your network player must be accessible** - deployed and responding to HTTP requests
- **Same tool used for official tournament** - the instructor uses this same tool with the reference engine for grading
