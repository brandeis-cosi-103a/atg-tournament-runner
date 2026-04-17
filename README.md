# ATG Tournament Runner

Run practice tournaments for ATG (Automation: The Game) against built-in bots and your deployed network player. **This is the same tool used for the official class tournament.**

## Quick Start

```bash
docker run --rm \
  -p 8081:8081 \
  -v $(pwd)/engine.jar:/app/engine.jar \
  -v $(pwd)/data:/app/data \
  -e TOURNAMENT_ENGINE_JAR=/app/engine.jar \
  -e TOURNAMENT_ENGINE_CLASS=edu.brandeis.cosi103a.engine.GameEngine \
  -e TOURNAMENT_PER_CALL_TIMEOUT_SECONDS=10 \
  -e TOURNAMENT_GAME_BUDGET_SECONDS=30 \
  ghcr.io/brandeis-cosi-103a/atg-tournament-runner
```

Open **http://localhost:8081**, add your player URL and some bots, click **Run Tournament**.

## Configuration

All configuration is via environment variables passed to `docker run` with `-e`:

| Variable | Required | Description |
|---|---|---|
| `TOURNAMENT_ENGINE_JAR` | Yes | Path to engine JAR inside the container |
| `TOURNAMENT_ENGINE_CLASS` | Yes | Fully-qualified engine class name |
| `TOURNAMENT_PER_CALL_TIMEOUT_SECONDS` | No | Hard timeout per `/decide` call (default: 0 = disabled). **The real tournament uses `10`.** |
| `TOURNAMENT_GAME_BUDGET_SECONDS` | No | Max cumulative decision time per player per game (default: 0 = disabled). **The real tournament uses `30`.** |

Both timeout variables must be > 0 to enable time budgets. When a player exceeds a timeout, that decision is forfeited (a passive default is used). When the game budget is exceeded, all remaining decisions in that game are forfeited. The player keeps their actual VP score.

## Adding Players

In the web UI:

- **Built-in bots**: Select from the dropdown (NaiveBigMoneyPlayer, ActionHeavyPlayer, RandomPlayer)
- **Your network player**: Select "URL (Network Player)" and enter your server's base URL (e.g., `https://my-player.azurecontainerapps.io`). Must expose `POST /decide` and `POST /log-event`.

You need at least 4 players to start a tournament.

## What to Check

- Your player appears in results and isn't forfeiting every decision
- You're beating RandomPlayer
- No timeouts — if you see forfeits, your `/decide` is too slow (must respond within 10s, aim for under 2s)
- Run a few tournaments to confirm stability

## How It Works

A tournament has multiple **rounds**. Each round randomly selects 10 action cards for the kingdom, shuffles players into tables of 3-4, and runs games in parallel. Results are scored with **TrueSkill** ratings. Before the tournament starts, all network player URLs are health-checked — unreachable players are excluded.

## Troubleshooting

**Player forfeiting every decision?** Your `/decide` endpoint is too slow or unreachable. Check your server logs and verify it responds within 10 seconds.

**Connection errors?** If your player is running locally, use your machine's IP instead of `localhost` (Docker can't reach the host's localhost).

**Engine class not found?** Make sure your JAR is a fat/shaded JAR with all dependencies, and the class name is fully-qualified.

## Advanced

### Custom Player JARs

Mount additional JARs to add local bot players:

```bash
docker run --rm \
  -p 8081:8081 \
  -v $(pwd)/engine.jar:/app/engine.jar \
  -v $(pwd)/my-player.jar:/app/player.jar \
  -v $(pwd)/data:/app/data \
  -e TOURNAMENT_ENGINE_JAR=/app/engine.jar \
  -e TOURNAMENT_ENGINE_CLASS=edu.brandeis.cosi103a.engine.GameEngine \
  -e TOURNAMENT_PER_CALL_TIMEOUT_SECONDS=10 \
  -e TOURNAMENT_GAME_BUDGET_SECONDS=30 \
  -e CLASSPATH=/app/runner.jar:/app/player.jar \
  ghcr.io/brandeis-cosi-103a/atg-tournament-runner
```

Any class implementing `Player` with a zero-arg or `String` constructor is auto-discovered and appears in the dropdown.

### Output Format

Results are written to the mounted `data/` directory as JSON files (`tournament.json`, `round-01.json`, etc.). Re-running with the same tournament name resumes from where it left off.
