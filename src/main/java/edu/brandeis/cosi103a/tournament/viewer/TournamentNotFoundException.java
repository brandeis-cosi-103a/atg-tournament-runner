package edu.brandeis.cosi103a.tournament.viewer;

/**
 * Thrown when a requested tournament or its data files cannot be found.
 */
public class TournamentNotFoundException extends RuntimeException {
    public TournamentNotFoundException(String message) {
        super(message);
    }
}
