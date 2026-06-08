 package com.loot.gateway;                                                           
                                                                                        
    public record CollectionResult(                                                     
        boolean isSuccessful,       // True if the request was successfully initiated   
        String gatewayReference,    // The gateway's reference ID (e.g.,CheckoutRequestID from M-Pesa)                                                        
        String responseMessage      // Description or error message from the gateway    
    ) {}                 