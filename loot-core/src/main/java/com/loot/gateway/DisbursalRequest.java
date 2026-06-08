package com.loot.gateway;                                                           
                                                                                        
    import java.math.BigDecimal;                                                        
                                                                                        
    public record DisbursalRequest(                                                     
        String transactionId,                                                           
        String recipientPhone,                                                          
        BigDecimal amount,                                                              
        String description                                                              
    ) {}