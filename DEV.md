# Development Commands

## Build

```bash
cd /workspaces/atg-tournament-runner && mvn package -q -DskipTests
```

## Run Server

```bash
java -Dtournament.show-delay-option=true \
     -Dtournament.game-thread-pool-size=64 \
     -Dtournament.engine-jar=/workspaces/atg-reference-impl/automation/engine/target/engine-1.0-SNAPSHOT-with-deps-jar-with-dependencies.jar \
     -Dtournament.engine-class=edu.brandeis.cosi103a.engine.GameEngine \
     -cp /workspaces/atg-tournament-runner/target/atg-tournament-runner-1.0.0-SNAPSHOT-shaded.jar \
     edu.brandeis.cosi103a.tournament.viewer.TournamentViewerApplication
```

## Run Server in Background

```bash
java -Dtournament.show-delay-option=true \
     -Dtournament.game-thread-pool-size=64 \
     -Dtournament.engine-jar=/workspaces/atg-reference-impl/automation/engine/target/engine-1.0-SNAPSHOT-with-deps-jar-with-dependencies.jar \
     -Dtournament.engine-class=edu.brandeis.cosi103a.engine.GameEngine \
     -cp /workspaces/atg-tournament-runner/target/atg-tournament-runner-1.0.0-SNAPSHOT-shaded.jar \
     edu.brandeis.cosi103a.tournament.viewer.TournamentViewerApplication > /tmp/server.log 2>&1 &
```

## Stop Server

```bash
pkill -f "TournamentViewerApplication"
```

## Build + Restart (one-liner)

```bash
cd /workspaces/atg-tournament-runner && mvn package -q -DskipTests && pkill -f "TournamentViewerApplication" 2>/dev/null; sleep 1; java -Dtournament.show-delay-option=true -Dtournament.game-thread-pool-size=64 -Dtournament.engine-jar=/workspaces/atg-reference-impl/automation/engine/target/engine-1.0-SNAPSHOT-with-deps-jar-with-dependencies.jar -Dtournament.engine-class=edu.brandeis.cosi103a.engine.GameEngine -cp /workspaces/atg-tournament-runner/target/atg-tournament-runner-1.0.0-SNAPSHOT-shaded.jar edu.brandeis.cosi103a.tournament.viewer.TournamentViewerApplication > /tmp/server.log 2>&1 &
```

## Check Server Log

```bash
tail -f /tmp/server.log
```

## Server URL

http://localhost:8081/
