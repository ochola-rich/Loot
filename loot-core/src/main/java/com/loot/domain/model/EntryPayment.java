package com.loot.domain.model;

import  lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.loot.crypto.PhoneNumberConverter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "entry_payments")
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EntryPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name="tournament_id", nullable=false)
    private long tournamentId;

    @Column(name="player_phone", nullable=false)
    @Convert(converter = PhoneNumberConverter.class)
    private String playerPhone;

    @Column(name="amount_kes", nullable=false)
    private BigDecimal amountKes;

    @Column(nullable = false)
    private String gateway;

    @Column(nullable = false)
    private String status;

    @Column(name = "mpesa_ref")
    private String mpesaRef;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
