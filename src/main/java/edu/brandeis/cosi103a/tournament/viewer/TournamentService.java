package edu.brandeis.cosi103a.tournament.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for reading tournament data from the filesystem.
 */
@Service
public class TournamentService {

    private final Path dataDir;
    private final ObjectMapper objectMapper;

    public TournamentService(
            @Value("${tournament.data-dir:./data}") String dataDir,
            ObjectMapper objectMapper) {
        this.dataDir = Path.of(dataDir);
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all tournament subdirectories with summary info.
     */
    public List<TournamentSummary> listTournaments() throws IOException {
        List<TournamentSummary> result = new ArrayList<>();
        if (!Files.isDirectory(dataDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                String name = entry.getFileName().toString();
                int playerCount = 0;
                int eventCount = 0;
                Path tapePath = entry.resolve("tape.json");
                if (Files.exists(tapePath)) {
                    JsonNode tape = objectMapper.readTree(tapePath.toFile());
                    if (tape.has("players")) {
                        playerCount = tape.get("players").size();
                    }
                    if (tape.has("events")) {
                        eventCount = tape.get("events").size();
                    }
                }
                result.add(new TournamentSummary(name, playerCount, eventCount));
            }
        }
        return result;
    }

    /**
     * Returns the raw tape.json content for a tournament.
     */
    public String getTapeJson(String name) throws IOException {
        validateName(name);
        Path tapePath = dataDir.resolve(name).resolve("tape.json");
        if (!Files.exists(tapePath)) {
            throw new TournamentNotFoundException("No tape.json for tournament: " + name);
        }
        return Files.readString(tapePath);
    }

    /**
     * Returns the raw tournament.json content for a tournament.
     */
    public String getTournamentJson(String name) throws IOException {
        validateName(name);
        Path metaPath = dataDir.resolve(name).resolve("tournament.json");
        if (!Files.exists(metaPath)) {
            throw new TournamentNotFoundException("No tournament.json for tournament: " + name);
        }
        return Files.readString(metaPath);
    }

    /**
     * Writes a ZIP file containing tournament data (excluding tape.json) to the response.
     */
    public void writeTournamentZip(String name, HttpServletResponse response) throws IOException {
        validateName(name);
        Path tournamentDir = dataDir.resolve(name);
        if (!Files.isDirectory(tournamentDir)) {
            throw new TournamentNotFoundException("Tournament not found: " + name);
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + name + ".zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream());
             DirectoryStream<Path> stream = Files.newDirectoryStream(tournamentDir)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                // Skip tape.json (derivative data) and non-JSON files
                if (!fileName.endsWith(".json") || fileName.equals("tape.json")) {
                    continue;
                }
                ZipEntry entry = new ZipEntry(name + "/" + fileName);
                zos.putNextEntry(entry);
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
    }

    private static void validateName(String name) {
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new TournamentNotFoundException("Invalid tournament name: " + name);
        }
    }

    public record TournamentSummary(String name, int playerCount, int eventCount) {}
}
