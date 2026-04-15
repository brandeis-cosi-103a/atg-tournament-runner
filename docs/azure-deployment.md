# Azure Deployment Runbook

Deploy the COSI 103A tournament runner (and optionally the reference player server) on Azure Container Apps. Designed for a class of up to 22 students.

## 1. Prerequisites

- **Azure CLI** (`az`) installed and logged in (`az login`)
- **Docker** installed and running
- **Java 24** and **Maven** on your build machine
- **GitHub access** to `atg-reference-impl` and `atg-tournament-runner` (private repos)

## 2. Build Artifacts Locally

### 2a. Build the reference engine JAR

```bash
cd /path/to/atg-reference-impl/automation
mvn clean package -DskipTests
```

The engine JAR is at:
```
automation/engine/target/engine-1.0-SNAPSHOT-with-deps-jar-with-dependencies.jar
```

### 2b. Build the tournament runner shaded JAR

```bash
cd /path/to/atg-tournament-runner
mvn clean package -DskipTests
```

The shaded JAR is at:
```
target/atg-tournament-runner-1.0.0-SNAPSHOT-shaded.jar
```

### 2c. Build the reference player server JAR (optional)

This was already built in step 2a. The JAR is at:
```
automation/network-player-server/target/network-player-server-1.0-SNAPSHOT.jar
```

## 3. Setup Azure Resources

Set shell variables for all resource names. Adjust these once and every command below will use them.

```bash
RESOURCE_GROUP=cosi103a-tournament
LOCATION=eastus
ACR_NAME=cosi103aacr          # must be globally unique, lowercase alphanumeric
ENVIRONMENT=cosi103a-env
```

Create the resource group, container registry, and Container Apps environment:

```bash
az group create --name $RESOURCE_GROUP --location $LOCATION

az acr create \
  --resource-group $RESOURCE_GROUP \
  --name $ACR_NAME \
  --sku Basic \
  --admin-enabled true

az containerapp env create \
  --name $ENVIRONMENT \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION
```

Get the ACR login server and credentials (needed for image push and Container Apps pull):

```bash
ACR_SERVER=$(az acr show --name $ACR_NAME --query loginServer -o tsv)
ACR_PASSWORD=$(az acr credential show --name $ACR_NAME --query "passwords[0].value" -o tsv)

az acr login --name $ACR_NAME
```

## 4. Deploy Reference Player Server (Optional)

Use this for smoke testing before the real tournament. It serves the BigMoneyPlayer strategy (or whichever `player.type` you configure).

### 4a. Build and push Docker image

The network-player-server module does not ship its own Dockerfile, so create one in a temporary build context:

```bash
PLAYER_SERVER_DIR=$(mktemp -d)

cp /path/to/atg-reference-impl/automation/network-player-server/target/network-player-server-1.0-SNAPSHOT.jar \
   "$PLAYER_SERVER_DIR/app.jar"

cat > "$PLAYER_SERVER_DIR/Dockerfile" <<'EOF'
FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
COPY app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

docker build -t "$ACR_SERVER/atg-player-server:latest" "$PLAYER_SERVER_DIR"
docker push "$ACR_SERVER/atg-player-server:latest"

rm -rf "$PLAYER_SERVER_DIR"
```

### 4b. Create the Container App

```bash
az containerapp create \
  --name atg-player-server \
  --resource-group $RESOURCE_GROUP \
  --environment $ENVIRONMENT \
  --image "$ACR_SERVER/atg-player-server:latest" \
  --registry-server "$ACR_SERVER" \
  --registry-username "$ACR_NAME" \
  --registry-password "$ACR_PASSWORD" \
  --target-port 8080 \
  --ingress external \
  --min-replicas 1 \
  --max-replicas 1 \
  --cpu 1.0 \
  --memory 2.0Gi
```

### 4c. Verify

```bash
PLAYER_FQDN=$(az containerapp show \
  --name atg-player-server \
  --resource-group $RESOURCE_GROUP \
  --query properties.configuration.ingress.fqdn -o tsv)

echo "Player server URL: https://$PLAYER_FQDN"

# Health check -- should return 200 (Spring Boot actuator or just no 404)
curl -s -o /dev/null -w "%{http_code}" "https://$PLAYER_FQDN/decide" -X POST \
  -H "Content-Type: application/json" -d '{}'
# Expect 400 (bad request is fine -- means the server is up and rejecting invalid input)
```

## 5. Deploy Tournament Runner

### 5a. Copy engine JAR into the tournament runner build context

```bash
cp /path/to/atg-reference-impl/automation/engine/target/engine-1.0-SNAPSHOT-with-deps-jar-with-dependencies.jar \
   /path/to/atg-tournament-runner/engine.jar
```

### 5b. Build and push Docker image

```bash
cd /path/to/atg-tournament-runner

docker build -t "$ACR_SERVER/atg-tournament-runner:latest" .
docker push "$ACR_SERVER/atg-tournament-runner:latest"
```

### 5c. Create the Container App

```bash
az containerapp create \
  --name atg-tournament-runner \
  --resource-group $RESOURCE_GROUP \
  --environment $ENVIRONMENT \
  --image "$ACR_SERVER/atg-tournament-runner:latest" \
  --registry-server "$ACR_SERVER" \
  --registry-username "$ACR_NAME" \
  --registry-password "$ACR_PASSWORD" \
  --target-port 8081 \
  --ingress external \
  --min-replicas 1 \
  --max-replicas 1 \
  --cpu 2.0 \
  --memory 4.0Gi
```

The tournament runner needs more CPU/memory than the player server because it orchestrates all games concurrently (64-thread pool by default).

### 5d. Verify

```bash
RUNNER_FQDN=$(az containerapp show \
  --name atg-tournament-runner \
  --resource-group $RESOURCE_GROUP \
  --query properties.configuration.ingress.fqdn -o tsv)

echo "Tournament runner URL: https://$RUNNER_FQDN"

# Web UI should load
curl -s -o /dev/null -w "%{http_code}" "https://$RUNNER_FQDN/"
# Expect 200

# API should respond
curl -s "https://$RUNNER_FQDN/api/tournaments"
# Expect [] (empty JSON array)
```

## 6. Smoke Test with Small Tournament

### 6a. Create a test CSV with reference player URLs

If you deployed the reference player server in step 4, use it. Otherwise, substitute any reachable player server URLs.

```bash
cat > /tmp/test-players.csv <<EOF
RefPlayer-1,https://$PLAYER_FQDN
RefPlayer-2,https://$PLAYER_FQDN
RefPlayer-3,https://$PLAYER_FQDN
RefPlayer-4,https://$PLAYER_FQDN
EOF
```

The tournament requires at least 4 players.

### 6b. Generate tournament config JSON

```bash
./scripts/make-tournament.sh \
  --rounds 2 \
  --games-per-player 5 \
  --name "Smoke Test" \
  /tmp/test-players.csv > /tmp/test-tournament.json
```

Inspect the generated config:

```bash
cat /tmp/test-tournament.json
```

### 6c. POST to the tournament runner API

```bash
curl -X POST "https://$RUNNER_FQDN/api/tournaments" \
  -H "Content-Type: application/json" \
  -d @/tmp/test-tournament.json
```

Expected response (HTTP 202):
```json
{
  "tournamentId": "<uuid>",
  "tournamentName": "Smoke Test",
  "status": "ACCEPTED",
  "players": [...]
}
```

### 6d. Monitor progress

Open the web UI in your browser:
```
https://$RUNNER_FQDN/
```

Or poll the status API:
```bash
TOURNAMENT_ID=<uuid-from-previous-response>
curl -s "https://$RUNNER_FQDN/api/tournaments/$TOURNAMENT_ID/status"
```

The smoke test should complete within a minute or two. Once it finishes, verify results are visible in the web UI.

## 7. Run Real Tournament

### 7a. Prepare student player CSV

Export student Google Form responses to CSV. The CSV format is `name,url` with no header row:

```
Alice,https://alice-player.azurecontainerapps.io
Bob,https://bob-player.eastus.azurecontainerapps.io
Charlie,https://charlie-atg.azurewebsites.net
...
```

Save as `students.csv`. If the Google Form CSV has extra columns or a header, clean it up first.

### 7b. Generate tournament config

For a class tournament with 22 students, recommended settings:

```bash
./scripts/make-tournament.sh \
  --rounds 15 \
  --games-per-player 25 \
  --name "COSI 103A Tournament $(date +%Y-%m-%d)" \
  students.csv > tournament.json
```

This produces ~15 rounds with each student playing ~25 games per round, giving enough data for meaningful TrueSkill rankings.

### 7c. Start the tournament

```bash
curl -X POST "https://$RUNNER_FQDN/api/tournaments" \
  -H "Content-Type: application/json" \
  -d @tournament.json
```

### 7d. Monitor via web UI

Open `https://$RUNNER_FQDN/` in your browser. The UI shows real-time progress via WebSocket, including:
- Round-by-round results
- TrueSkill ratings
- Per-player win/loss statistics

A full tournament with 22 players, 15 rounds, and 25 games per player typically takes 5-15 minutes depending on student server response times.

### 7e. Download results

Once complete, download the tournament data ZIP from the web UI, or via the API:

```bash
curl -o results.zip "https://$RUNNER_FQDN/api/tournaments/<tournament-name>/download.zip"
```

## 8. Cleanup

Delete the entire resource group to remove all resources (Container Apps, ACR, environment):

```bash
az group delete --name $RESOURCE_GROUP --yes --no-wait
```

This is asynchronous. To confirm deletion:

```bash
az group show --name $RESOURCE_GROUP 2>/dev/null || echo "Resource group deleted"
```

## Troubleshooting

**Container App won't start / crashes:**
```bash
az containerapp logs show \
  --name atg-tournament-runner \
  --resource-group $RESOURCE_GROUP \
  --follow
```

**Tournament POST returns 503 "engine not configured":**
The `TOURNAMENT_ENGINE_JAR` or `TOURNAMENT_ENGINE_CLASS` env vars are missing. Verify the engine.jar was copied into the Docker build context before building the image.

**Student player server unreachable during tournament:**
The tournament runner handles player timeouts gracefully. Unreachable players forfeit their games but don't block the tournament. Check that student Container Apps have external ingress enabled.

**Need to update the image after a rebuild:**
```bash
docker build -t "$ACR_SERVER/atg-tournament-runner:latest" .
docker push "$ACR_SERVER/atg-tournament-runner:latest"

az containerapp update \
  --name atg-tournament-runner \
  --resource-group $RESOURCE_GROUP \
  --image "$ACR_SERVER/atg-tournament-runner:latest"
```
