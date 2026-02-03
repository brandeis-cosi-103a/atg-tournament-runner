package edu.brandeis.cosi103a.tournament.viewer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO for tournament configuration requests.
 */
public record TournamentConfigRequest(
    @JsonProperty("tournamentName") @NotBlank String tournamentName,
    @JsonProperty("rounds") @Min(1) int rounds,
    @JsonProperty("gamesPerTable") @Min(1) int gamesPerTable,
    @JsonProperty("players") @NotEmpty @Valid List<PlayerConfigRequest> players
) {}
