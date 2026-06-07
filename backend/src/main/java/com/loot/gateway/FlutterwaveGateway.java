package com.loot.gateway;
  
    import org.springframework.stereotype.Component;
    import java.util.UUID;
  
    @Component("flutterwaveGateway")
    public class FlutterwaveGateway implements PaymentGateway {
  
        @Override
        public CollectionResult initiateCollection(CollectionRequest req) {             
            // Here you will eventually call Flutterwave's Charge API
            System.out.println("Initiating Flutterwave Collection for " + req.playerPhone() + " amount: " + req.amount());
            return new CollectionResult(true, "flw_col_" + UUID.randomUUID().toString().substring(0, 8), "Collection Initiated");
        }
  
        @Override
        public DisbursalResult initiatePayout(DisbursalRequest req) {
            // Here you will eventually call Flutterwave's Transfer API
            System.out.println("Initiating Flutterwave Payout to " + req.recipientPhone() + " amount: " + req.amount());
            return new DisbursalResult(true, "flw_tx_" + UUID.randomUUID().toString().substring(0, 8), "Transfer Initiated");
        }
    }
