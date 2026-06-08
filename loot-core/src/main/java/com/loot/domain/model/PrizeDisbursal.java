package com.loot.domain.model;                                                      
                                                                                    
import jakarta.persistence.*;                                                       
import lombok.Getter;                                                               
import lombok.Setter;                                                               
import lombok.NoArgsConstructor;                                                    
import lombok.AllArgsConstructor;                                                   
import java.math.BigDecimal;                                                        
                                                                                    
@Entity                                                                             
@Table(name = "prize_disbursals")                                                   
@Getter                                                                             
@Setter                                                                             
@NoArgsConstructor                                                                  
@AllArgsConstructor                                                                 
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
}
