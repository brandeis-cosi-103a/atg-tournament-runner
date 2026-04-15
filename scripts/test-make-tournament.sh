#!/usr/bin/env bash
# Tests for make-tournament.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$SCRIPT_DIR/make-tournament.sh"
PASS=0
FAIL=0
TMPDIR="$(mktemp -d)"

cleanup() { rm -rf "$TMPDIR"; }
trap cleanup EXIT

assert_eq() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$expected" == "$actual" ]]; then
    echo "  PASS: $label"
    PASS=$((PASS + 1))
  else
    echo "  FAIL: $label"
    echo "    expected: $expected"
    echo "    actual:   $actual"
    FAIL=$((FAIL + 1))
  fi
}

assert_contains() {
  local label="$1" needle="$2" haystack="$3"
  if [[ "$haystack" == *"$needle"* ]]; then
    echo "  PASS: $label"
    PASS=$((PASS + 1))
  else
    echo "  FAIL: $label"
    echo "    expected to contain: $needle"
    echo "    actual: $haystack"
    FAIL=$((FAIL + 1))
  fi
}

assert_exit_code() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$expected" == "$actual" ]]; then
    echo "  PASS: $label"
    PASS=$((PASS + 1))
  else
    echo "  FAIL: $label"
    echo "    expected exit code: $expected"
    echo "    actual exit code:   $actual"
    FAIL=$((FAIL + 1))
  fi
}

# Create sample CSV
cat > "$TMPDIR/players.csv" <<'CSV'
Alice,http://alice.example.com
Bob,http://bob.example.com
CSV

echo "=== Test: script is executable ==="
if [[ -x "$SCRIPT" ]]; then
  echo "  PASS: script is executable"
  PASS=$((PASS + 1))
else
  echo "  FAIL: script is not executable"
  FAIL=$((FAIL + 1))
fi

echo "=== Test: --help shows usage ==="
output=$("$SCRIPT" --help 2>&1) || true
assert_contains "help mentions usage" "usage" "$(echo "$output" | tr '[:upper:]' '[:lower:]')"

echo "=== Test: missing CSV arg shows usage ==="
output=$("$SCRIPT" 2>&1) && rc=0 || rc=$?
assert_exit_code "exits non-zero without CSV" "1" "$rc"
assert_contains "missing arg shows usage" "usage" "$(echo "$output" | tr '[:upper:]' '[:lower:]')"

echo "=== Test: default flags with CSV ==="
output=$("$SCRIPT" "$TMPDIR/players.csv" 2>/dev/null)
# Validate JSON structure
if command -v jq &>/dev/null; then
  rounds=$(echo "$output" | jq -r '.rounds')
  gamesPerPlayer=$(echo "$output" | jq -r '.gamesPerPlayer')
  playerCount=$(echo "$output" | jq '.players | length')
  firstName=$(echo "$output" | jq -r '.players[0].name')
  firstUrl=$(echo "$output" | jq -r '.players[0].url')
  firstDelay=$(echo "$output" | jq -r '.players[0].delay')
  tournamentName=$(echo "$output" | jq -r '.tournamentName')

  assert_eq "default rounds is 3" "3" "$rounds"
  assert_eq "default gamesPerPlayer is 10" "10" "$gamesPerPlayer"
  assert_eq "player count is 2" "2" "$playerCount"
  assert_eq "first player name" "Alice" "$firstName"
  assert_eq "first player url" "http://alice.example.com" "$firstUrl"
  assert_eq "first player delay is false" "false" "$firstDelay"
  assert_contains "tournament name contains date" "Tournament" "$tournamentName"
else
  # Fallback: check for key strings in raw JSON
  assert_contains "output contains rounds" '"rounds"' "$output"
  assert_contains "output contains gamesPerPlayer" '"gamesPerPlayer"' "$output"
  assert_contains "output contains Alice" '"Alice"' "$output"
  assert_contains "output contains delay false" '"delay": false' "$output"
  echo "  (jq not available; limited validation)"
fi

echo "=== Test: custom flags ==="
output=$("$SCRIPT" --rounds 15 --games-per-player 25 --name "My Tournament" "$TMPDIR/players.csv" 2>/dev/null)
if command -v jq &>/dev/null; then
  rounds=$(echo "$output" | jq -r '.rounds')
  gamesPerPlayer=$(echo "$output" | jq -r '.gamesPerPlayer')
  tournamentName=$(echo "$output" | jq -r '.tournamentName')
  assert_eq "custom rounds is 15" "15" "$rounds"
  assert_eq "custom gamesPerPlayer is 25" "25" "$gamesPerPlayer"
  assert_eq "custom tournament name" "My Tournament" "$tournamentName"
else
  assert_contains "output contains rounds 15" '"rounds": 15' "$output"
  assert_contains "output contains gamesPerPlayer 25" '"gamesPerPlayer": 25' "$output"
fi

echo "=== Test: output is valid JSON ==="
output=$("$SCRIPT" "$TMPDIR/players.csv" 2>/dev/null)
if command -v jq &>/dev/null; then
  echo "$output" | jq . > /dev/null 2>&1 && valid="yes" || valid="no"
  assert_eq "output is valid JSON" "yes" "$valid"
else
  # Basic check: starts with { and ends with }
  trimmed=$(echo "$output" | sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//')
  starts=$(echo "$trimmed" | head -c1)
  ends=$(echo "$trimmed" | tail -c2 | head -c1)
  if [[ "$starts" == "{" && "$ends" == "}" ]]; then
    echo "  PASS: output looks like JSON (basic check)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL: output does not look like JSON"
    FAIL=$((FAIL + 1))
  fi
fi

echo "=== Test: nonexistent CSV file ==="
output=$("$SCRIPT" "$TMPDIR/nonexistent.csv" 2>&1) && rc=0 || rc=$?
assert_exit_code "exits non-zero for missing CSV" "1" "$rc"

echo "=== Test: single-player CSV ==="
cat > "$TMPDIR/single.csv" <<'CSV'
Solo,http://solo.example.com
CSV
output=$("$SCRIPT" "$TMPDIR/single.csv" 2>/dev/null)
if command -v jq &>/dev/null; then
  playerCount=$(echo "$output" | jq '.players | length')
  assert_eq "single player count" "1" "$playerCount"
fi

echo ""
echo "================================"
echo "Results: $PASS passed, $FAIL failed"
echo "================================"
[[ $FAIL -eq 0 ]] && exit 0 || exit 1
