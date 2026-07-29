package com.loot.domain.repository;

import com.loot.domain.model.GatewayTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GatewayTransactionRepository extends JpaRepository<GatewayTransaction, Long> {

    Optional<GatewayTransaction> findByIdempotencyKey(String idempotencyKey);
}
