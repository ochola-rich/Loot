package com.loot.domain.model;

import  lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "tournaments")
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Tournament {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @Column(nullable= false)
  private String name;

  @Column(name = "entry_fee_kes", nullable = false)
  private BigDecimal entryFeeKes;

  @Column(name = "max_entries", nullable=false)
  private Integer maxEntries;

  @Column(nullable=false)
  private String status;

  @CreatedDate
  @Column(name="created_at", nullable = false, updatable=false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
