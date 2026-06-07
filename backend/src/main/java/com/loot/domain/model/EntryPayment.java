package com.loot.domain.model;

import  lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "entry_payments")
@AllArgsConstructor
@NoArgsConstructor

public class EntryPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name="tournament_id", nullable=false)
    private long tournamentId;

    @Column(name="player_phone", nullable=false)
    private String playerPhone;

    @Column(name="amount_kes", nullable=false)
    private BigDecimal amountKes;

    @Column(nullable = false)
    private String gateway;

    @Column(nullable = false)
    private String status;

    @Column(name = "mpesa_ref")
    private String mpesaRef;
}
