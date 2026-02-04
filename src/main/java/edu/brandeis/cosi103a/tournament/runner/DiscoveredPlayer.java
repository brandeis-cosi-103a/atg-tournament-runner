package edu.brandeis.cosi103a.tournament.runner;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata for a discovered Player implementation on the classpath.
 *
 * @param simpleName          simple class name (e.g., "BigMoneyPlayer")
 * @param className           fully-qualified class name (e.g., "com.example.BigMoneyPlayer")
 * @param displayName         name to show in UI (simple name if unique, full name if duplicates exist)
 * @param description         optional description (from PlayerDescription annotation if present)
 * @param hasZeroArgConstructor whether the class has a public zero-argument constructor
 * @param hasNameConstructor  whether the class has a public String (name) constructor
 */
public record DiscoveredPlayer(
        @JsonProperty("simpleName") String simpleName,
        @JsonProperty("className") String className,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("description") String description,
        @JsonProperty("hasZeroArgConstructor") boolean hasZeroArgConstructor,
        @JsonProperty("hasNameConstructor") boolean hasNameConstructor
) {
    /**
     * Returns whether this player can be instantiated.
     * A player is instantiable if it has either a zero-arg constructor or a String (name) constructor.
     */
    public boolean isInstantiable() {
        return hasZeroArgConstructor || hasNameConstructor;
    }
}
