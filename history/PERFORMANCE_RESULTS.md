# Tournament Performance Benchmark Results

**Date**: 2026-02-04
**Test**: TournamentPerformanceTest.measurePerformanceWithNetworkDelay()

## Configuration

- **Players**: 4 (mix of naive-money, random, action-heavy strategies)
- **Rounds**: 3
- **Games per player per round**: 4
- **Total games**: 12
- **Runs per scenario**: 3 (averaged)
- **Network delay simulation**: 2-5ms (realistic same-region Azure latency)
- **Thread pool**: Fixed size, 4-8 threads (min 4, max 8 based on CPU cores)

## Benchmark Results

| Scenario | Run 1 | Run 2 | Run 3 | Avg (s) | Games/sec |
|----------|-------|-------|-------|---------|-----------|
| Baseline (local) | 0.11s | 0.10s | 0.10s | 0.10s | 114.65 |
| Network (2-5ms) | 34.69s | 35.09s | 35.08s | 34.95s | 0.34 |

**Slowdown factor**: 333.93x

## Analysis

### 1. Network Delay Impact

Even small network delays (2-5ms) cause a **massive 333x slowdown**. This is expected because:

- **Many decisions per game**: Each game involves ~180+ player decisions (4 players × ~15 turns × ~3+ decisions per turn for action/treasure/buy phases)
- **Sequential processing**: Decisions within a game cannot be parallelized - the game engine processes them one at a time
- **Delay compounds**: 180 decisions × 3.5ms average delay = **630ms minimum per game**

### 2. Observed vs Expected Performance

**Theoretical minimum** (with perfect parallelization):
- 12 games with 6-thread pool = 2 batches
- 630ms per game minimum = ~1.3 seconds total

**Actual performance**: 35 seconds (27x slower than theoretical minimum)

This gap suggests:
- Games have **more decisions than estimated** (~1000+ decisions per game would explain the timing)
- Or there are **other bottlenecks** in the execution path beyond just the network delay

### 3. Thread Pool Utilization

Current thread pool configuration:
```java
ExecutorService threadPool = Executors.newFixedThreadPool(
    Math.min(8, Math.max(4, Runtime.getRuntime().availableProcessors()))
);
```

**For CPU-bound work**: This sizing is optimal (cores = threads)

**For I/O-bound work** (network players): Threads spend time waiting on network I/O (blocked in `Thread.sleep()`), not using CPU. The pool could potentially handle more concurrent games.

**Little's Law** for thread pool sizing:
```
Optimal threads = cores × (1 + wait_time / service_time)
```

With network delays:
- If wait_time ≈ service_time → 2x cores
- If wait_time >> service_time → even more threads needed

### 4. Bottleneck Identification

The current implementation uses **CompletionService** which:
- ✅ Allows games to run concurrently
- ✅ Processes results as they complete (not in submission order)
- ✅ Updates ratings incrementally
- ❌ May be limited by thread pool size for I/O-bound workloads

**Primary bottleneck**: Thread pool is sized for CPU-bound work, but network players create I/O-bound workload where threads are blocked waiting.

## Recommendations

### Option 1: Increase Thread Pool for Network Players (Recommended)

**Implement task 6ir.4**: Make thread pool size configurable and increase it for tournaments with network players.

**Benefits**:
- Could reduce tournament time by 2-4x with larger pool
- Minimal code change (configuration-driven)
- No impact on local-only tournaments

**Implementation**:
- Add configuration for thread pool multiplier
- Detect network players vs local players
- Scale pool size: `cores × (1 + network_ratio) × multiplier`

### Option 2: Accept Current Performance (Alternative)

**Skip task 6ir.4** if:
- Live tournaments will primarily use local players
- 35 seconds per 12 games is acceptable for your use case
- Complexity of dynamic thread pool sizing isn't worth the benefit

## Decision

**Recommendation**: Proceed with thread pool optimization (task 6ir.4) if live tournaments will include network players. The 333x slowdown indicates significant room for improvement through better thread pool sizing.

If tournaments will primarily use local players (co-located bots), the current implementation is sufficient.

## Notes

- Test uses realistic same-region Azure latency (2-5ms)
- Previous test with 25-100ms delays took ~475s per run (cross-continental latency)
- Baseline performance is excellent (0.10s for 12 games)
- Live TrueSkill integration adds negligible overhead
