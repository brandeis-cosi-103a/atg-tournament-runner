#!/usr/bin/env bash
#
# make-tournament.sh -- Generate tournament config JSON from a CSV of players.
#
# Usage:
#   ./scripts/make-tournament.sh [OPTIONS] <players.csv>
#
# Options:
#   --rounds N             Number of rounds (default: 3)
#   --games-per-player N   Games per player per round (default: 10)
#   --name NAME            Tournament name (default: "Tournament YYYY-MM-DD")
#   --help                 Show this help message
#
# The CSV file should have one player per line: name,url (no header).
#
# Example:
#   ./scripts/make-tournament.sh --rounds 15 --games-per-player 25 players.csv
#   ./scripts/make-tournament.sh players.csv | curl -X POST http://localhost:8081/api/tournaments -H "Content-Type: application/json" -d @-
#
set -euo pipefail

# Defaults
rounds=3
games_per_player=10
tournament_name="Tournament $(date +%Y-%m-%d)"

usage() {
  cat <<'USAGE'
Usage: make-tournament.sh [OPTIONS] <players.csv>

Generate tournament config JSON from a CSV of players.

Options:
  --rounds N             Number of rounds (default: 3)
  --games-per-player N   Games per player per round (default: 10)
  --name NAME            Tournament name (default: "Tournament YYYY-MM-DD")
  --help                 Show this help message

CSV format (no header):
  name,url

Example:
  ./scripts/make-tournament.sh --rounds 15 --games-per-player 25 players.csv
USAGE
}

# Parse arguments
csv_file=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --rounds)
      rounds="$2"
      shift 2
      ;;
    --games-per-player)
      games_per_player="$2"
      shift 2
      ;;
    --name)
      tournament_name="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    -*)
      echo "Error: Unknown option '$1'" >&2
      usage >&2
      exit 1
      ;;
    *)
      csv_file="$1"
      shift
      ;;
  esac
done

# Validate CSV argument
if [[ -z "$csv_file" ]]; then
  echo "Error: Missing CSV file argument." >&2
  usage >&2
  exit 1
fi

if [[ ! -f "$csv_file" ]]; then
  echo "Error: CSV file not found: $csv_file" >&2
  exit 1
fi

# --- JSON generation ---

# Escape a string for safe JSON embedding
json_escape() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  s="${s//$'\n'/\\n}"
  s="${s//$'\r'/\\r}"
  s="${s//$'\t'/\\t}"
  printf '%s' "$s"
}

if command -v jq &>/dev/null; then
  # --- jq path: build JSON safely ---
  players_json="[]"
  while IFS=',' read -r name url || [[ -n "$name" ]]; do
    # Skip empty lines
    [[ -z "$name" ]] && continue
    # Trim whitespace
    name="$(echo "$name" | xargs)"
    url="$(echo "$url" | xargs)"
    players_json=$(echo "$players_json" | jq \
      --arg n "$name" \
      --arg u "$url" \
      '. + [{"name": $n, "url": $u, "delay": false}]')
  done < "$csv_file"

  jq -n \
    --arg tn "$tournament_name" \
    --argjson r "$rounds" \
    --argjson gpp "$games_per_player" \
    --argjson players "$players_json" \
    '{
      "tournamentName": $tn,
      "rounds": $r,
      "gamesPerPlayer": $gpp,
      "players": $players
    }'
else
  # --- printf/echo fallback: no jq available ---
  printf '{\n'
  printf '  "tournamentName": "%s",\n' "$(json_escape "$tournament_name")"
  printf '  "rounds": %d,\n' "$rounds"
  printf '  "gamesPerPlayer": %d,\n' "$games_per_player"
  printf '  "players": [\n'

  first=true
  while IFS=',' read -r name url || [[ -n "$name" ]]; do
    [[ -z "$name" ]] && continue
    name="$(echo "$name" | xargs)"
    url="$(echo "$url" | xargs)"
    if [[ "$first" == true ]]; then
      first=false
    else
      printf ',\n'
    fi
    printf '    {"name": "%s", "url": "%s", "delay": false}' \
      "$(json_escape "$name")" "$(json_escape "$url")"
  done < "$csv_file"

  printf '\n  ]\n'
  printf '}\n'
fi
