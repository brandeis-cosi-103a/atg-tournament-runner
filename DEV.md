# Development Commands

## Build

```bash
cd /workspaces/atg-tournament-runner && mvn package -q -DskipTests
```

## Build Players (atg-reference-impl)

```bash
cd /workspaces/atg-reference-impl/automation && mvn package -q -DskipTests
```

## Run Server (with all players)

```bash
cd /workspaces/atg-tournament-runner && \
java -Dtournament.show-delay-option=true \
     -Dtournament.game-thread-pool-size=64 \
     -Dtournament.engine-jar=/workspaces/atg-reference-impl/automation/engine/target/engine-1.0-SNAPSHOT-with-deps-jar-with-dependencies.jar \
     -Dtournament.engine-class=edu.brandeis.cosi103a.engine.GameEngine \
     -cp "target/atg-tournament-runner-1.0.0-SNAPSHOT-shaded.jar:\
/workspaces/atg-reference-impl/automation/big-money-player/target/big-money-player-1.0-SNAPSHOT-with-deps-jar-with-dependencies.jar:\
/workspaces/atg-reference-impl/automation/tech-debt-player/target/tech-debt-player-1.0-SNAPSHOT-with-deps-jar-with-dependencies.jar:\
/workspaces/atg-reference-impl/automation/attack-player/target/attack-player-1.0-SNAPSHOT.jar:\
/workspaces/atg-reference-impl/automation/engine-player/target/engine-player-1.0-SNAPSHOT.jar:\
/workspaces/atg-reference-impl/automation/random-player/target/random-player-1.0-SNAPSHOT.jar:\
/workspaces/atg-reference-impl/automation/player-common/target/player-common-1.0-SNAPSHOT.jar" \
     edu.brandeis.cosi103a.tournament.viewer.TournamentViewerApplication
```

## Run Server in Background

```bash
cd /workspaces/atg-tournament-runner && \
java -Dtournament.show-delay-option=true \
     -Dtournament.game-thread-pool-size=64 \
     -Dtournament.engine-jar=/workspaces/atg-reference-impl/automation/engine/target/engine-1.0-SNAPSHOT-with-deps-jar-with-dependencies.jar \
     -Dtournament.engine-class=edu.brandeis.cosi103a.engine.GameEngine \
     -cp "target/atg-tournament-runner-1.0.0-SNAPSHOT-shaded.jar:\
/workspaces/atg-reference-impl/automation/big-money-player/target/big-money-player-1.0-SNAPSHOT-with-deps-jar-with-dependencies.jar:\
/workspaces/atg-reference-impl/automation/tech-debt-player/target/tech-debt-player-1.0-SNAPSHOT-with-deps-jar-with-dependencies.jar:\
/workspaces/atg-reference-impl/automation/attack-player/target/attack-player-1.0-SNAPSHOT.jar:\
/workspaces/atg-reference-impl/automation/engine-player/target/engine-player-1.0-SNAPSHOT.jar:\
/workspaces/atg-reference-impl/automation/random-player/target/random-player-1.0-SNAPSHOT.jar:\
/workspaces/atg-reference-impl/automation/player-common/target/player-common-1.0-SNAPSHOT.jar" \
     edu.brandeis.cosi103a.tournament.viewer.TournamentViewerApplication > /tmp/server.log 2>&1 &
```

## Stop Server

```bash
pkill -f "TournamentViewerApplication"
```

## Check Server Log

```bash
tail -f /tmp/server.log
```

## Server URL

http://localhost:8081/

## Available Players

When running with the full classpath above, these players are discovered:

- **ActionHeavyPlayer** - Built-in, action card focused
- **AttackPlayer** - Parallelization + Ransomware/Evergreen Test combo
- **BigMoneyPlayer** - Classic big money strategy
- **EnginePlayer** - Engine-based player
- **NaiveBigMoneyPlayer** - Built-in, simplified big money
- **RandomPlayer** - Random decision making
- **TechDebtPlayer** - Attack-focused with money-first build order
