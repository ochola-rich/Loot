package com.loot.domain.model;                                                      
                                                                                    
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "prize_disbursals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PrizeDisbursal {                                                     
                                                                                    
    @Id                                                                             
    @GeneratedValue(strategy = GenerationType.IDENTITY)                             
    private Long id;                                                                
                                                                                    
    @Column(name = "tournament_id", nullable = false)                               
    private Long tournamentId;                                                      
                                                                                    
    @Column(name = "recipient_phone", nullable = false)                             
    private String recipientPhone;                                                  
                                                                                    
    @Column(name = "amount_kes", nullable = false)                                  
    private BigDecimal amountKes;                                                   
                                                                                    
    @Column(nullable = false)                                                       
    private String gateway; // e.g., "MPESA"                                        
                                                                                    
    @Column(nullable = false)
    private String status; // e.g., "PROCESSING", "DISBURSED", "FAILED"

    @Column(name = "gateway_ref")
    private String gatewayRef; // e.g., Daraja ConversationID, Flutterwave transfer id

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
