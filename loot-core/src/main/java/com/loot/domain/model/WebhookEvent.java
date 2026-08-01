package com.loot.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String gateway; // e.g., "MPESA", "FLUTTERWAVE"

    @Column(name = "event_type", nullable = false)
    private String eventType; // e.g., "STK_PUSH_REQUEST", "C2B_CONFIRMATION"

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(nullable = false)
    private String status; // e.g., "SENT", "RECEIVED", "PROCESSED", "FAILED"

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
