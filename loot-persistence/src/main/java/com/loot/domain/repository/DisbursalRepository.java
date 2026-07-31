package com.loot.domain.repository;

import com.loot.domain.model.PrizeDisbursal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisbursalRepository extends JpaRepository<PrizeDisbursal, Long> {

    List<PrizeDisbursal> findByTournamentIdAndStatus(long tournamentId, String status);

    Optional<PrizeDisbursal> findByGatewayRef(String gatewayRef);
}
