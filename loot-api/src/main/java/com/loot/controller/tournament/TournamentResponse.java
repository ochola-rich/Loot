package com.loot.controller.tournament;

import java.math.BigDecimal;
import java.time.Instant;

public record TournamentResponse(
        long id,
        String name,
        BigDecimal entryFeeKes,
        Integer maxEntries,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
