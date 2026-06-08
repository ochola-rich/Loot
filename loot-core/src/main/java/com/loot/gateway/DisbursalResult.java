package com.loot.gateway;                                                           
                                                                                        
    public record DisbursalResult(                                                      
        boolean isSuccessful,                                                           
        String gatewayReference,                                                        
        String responseMessage                                                          
    ) {} 