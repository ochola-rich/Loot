package com.loot.gateway.flutterwave;

/**
 * Builds the Flutterwave Transfers request body for a single payout.
 * account_bank "MPS" is Flutterwave's bank code for M-Pesa mobile money
 * payouts in Kenya. Hardcoded to KES for the same reason as the charge
 * side (t24) - currency lands with the Money value object in t28.
 *
 * Only single transfers (POST /v3/transfers) are wired here - bulk_transfers
 * doesn't fit PaymentGateway's one-DisbursalRequest-at-a-time contract, and
 * concurrent single payouts are handled by the virtual-thread dispatcher
 * (t35) instead.
 */
public class FlutterwaveTransferRequestFactory {

    static final String ACCOUNT_BANK = "MPS";
    private static final String CURRENCY = "KES";

    public FlutterwaveTransferRequest build(String transactionId, String recipientPhone, String amount,
                                             String narration) {
        return new FlutterwaveTransferRequest(
                ACCOUNT_BANK,
                recipientPhone,
                amount,
                CURRENCY,
                narration,
                transactionId
        );
    }
}
