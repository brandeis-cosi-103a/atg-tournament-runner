package edu.brandeis.cosi103a.tournament.runner;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for a single player in the tournament.
 *
 * @param id    lowercase identifier derived from name
 * @param name  display name
 * @param url   either a URL for network players, or "big-money"/"tech-debt" for local players
 * @param delay whether to wrap the player with a delay wrapper for testing
 */
public record PlayerConfig(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("url") String url,
    @JsonProperty("delay") boolean delay
) {}
