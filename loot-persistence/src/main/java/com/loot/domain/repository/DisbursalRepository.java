package com.loot.domain.repository;

import com.loot.domain.model.PrizeDisbursal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisbursalRepository extends JpaRepository<PrizeDisbursal, Long> {

    List<PrizeDisbursal> findByTournamentIdAndStatus(long tournamentId, String status);
}
