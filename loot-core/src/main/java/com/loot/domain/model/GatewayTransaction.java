package com.loot.domain.model;                                                      
                                                                                        
import jakarta.persistence.*;                                                       
import lombok.Getter;                                                               
import lombok.Setter;                                                               
import lombok.NoArgsConstructor;                                                    
import lombok.AllArgsConstructor;                                                   
import java.time.Instant;                                                           
                                                                                    
@Entity                                                                             
@Table(name = "gateway_transactions")                                               
@Getter                                                                             
@Setter                                                                             
@NoArgsConstructor                                                                  
@AllArgsConstructor                                                                 
public class GatewayTransaction {                                                   
                                                                                    
    @Id                                                                             
    @GeneratedValue(strategy = GenerationType.IDENTITY)                             
    private Long id;                                                                
                                                                                    
    @Column(name = "idempotency_key", nullable = false, unique = true)              
    private String idempotencyKey;                                                  
                                                                                    
    @Column(name = "raw_request", columnDefinition = "TEXT")                        
    private String rawRequest; // Store full JSON request payload                   
                                                                                    
    @Column(name = "raw_response", columnDefinition = "TEXT")                       
    private String rawResponse; // Store full JSON response payload                 
                                                                                    
    @Column(nullable = false)                                                       
    private String gateway; // e.g., "MPESA"                                        
                                                                                    
    @Column(name = "created_at", nullable = false, updatable = false)               
    private Instant createdAt = Instant.now();                                      
}                                         