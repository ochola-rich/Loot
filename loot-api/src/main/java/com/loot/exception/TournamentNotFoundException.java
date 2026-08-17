package com.loot.exception;

public class TournamentNotFoundException extends RuntimeException {

    public TournamentNotFoundException(long tournamentId) {
        super("Tournament " + tournamentId + " not found");
    }
}
