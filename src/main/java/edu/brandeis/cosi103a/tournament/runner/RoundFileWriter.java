package edu.brandeis.cosi103a.tournament.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Writes tournament metadata and round result files to disk.
 * Round files are written atomically to prevent partial writes.
 */
public class RoundFileWriter {

    private final Path outputDir;
    private final ObjectMapper objectMapper;

    public RoundFileWriter(Path outputDir) {
        this.outputDir = outputDir;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Writes the tournament.json metadata file.
     */
    public void writeTournamentMetadata(TournamentConfig config) throws IOException {
        Files.createDirectories(outputDir);
        Map<String, Object> metadata = Map.of(
            "name", config.name(),
            "config", Map.of(
                "rounds", config.rounds(),
                "gamesPerPlayer", config.gamesPerPlayer(),
                "maxTurns", config.maxTurns()
            ),
            "players", config.players()
        );
        Path target = outputDir.resolve("tournament.json");
        Path temp = outputDir.resolve("tournament.json.tmp");
        objectMapper.writeValue(temp.toFile(), metadata);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Writes a round-NN.json file atomically.
     */
    public void writeRound(RoundResult round) throws IOException {
        Files.createDirectories(outputDir);
        String filename = String.format("round-%02d.json", round.roundNumber());
        Path target = outputDir.resolve(filename);
        Path temp = outputDir.resolve(filename + ".tmp");
        objectMapper.writeValue(temp.toFile(), round);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Checks whether a round file already exists (for resume support).
     */
    public boolean roundExists(int roundNumber) {
        String filename = String.format("round-%02d.json", roundNumber);
        return Files.exists(outputDir.resolve(filename));
    }
}
