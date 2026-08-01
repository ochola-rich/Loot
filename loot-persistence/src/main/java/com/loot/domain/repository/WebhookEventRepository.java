package com.loot.domain.repository;

import com.loot.domain.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
}
