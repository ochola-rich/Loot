    package com.loot.gateway;                                                           
                                                                                        
    public interface PaymentGateway {                                                   
                                                                                        
        CollectionResult initiateCollection(CollectionRequest req);                     
                                                                                        
        DisbursalResult initiatePayout(DisbursalRequest req);                           
    }