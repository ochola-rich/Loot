package com.loot.domain.repository;

import com.loot.domain.model.EntryPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<EntryPayment, Long> {

    List<EntryPayment> findByTournamentIdAndStatus(long tournamentId, String status);

    Optional<EntryPayment> findByMpesaRef(String mpesaRef);

    long countByTournamentIdAndStatusNot(long tournamentId, String status);
}
