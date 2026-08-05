package com.loot.gateway.flutterwave;

/**
 * Builds the Flutterwave Transfers request body for a single payout.
 * account_bank "MPS" is Flutterwave's bank code for M-Pesa mobile money
 * payouts in Kenya - the plan never gave a confirmed bank code for UGX/GHS/
 * TZS mobile money transfers, so this factory (and FlutterwaveGateway)
 * still only supports KES payouts. Guessing a bank code for the others
 * risks silently misrouting a real payout, which is worse than not
 * supporting it yet.
 *
 * Only single transfers (POST /v3/transfers) are wired here - bulk_transfers
 * doesn't fit PaymentGateway's one-DisbursalRequest-at-a-time contract, and
 * concurrent single payouts are handled by the virtual-thread dispatcher
 * (t35) instead.
 */
public class FlutterwaveTransferRequestFactory {

    static final String KES_ACCOUNT_BANK = "MPS";

    public FlutterwaveTransferRequest build(String transactionId, String recipientPhone, String amount,
                                             String currency, String narration) {
        return new FlutterwaveTransferRequest(
                KES_ACCOUNT_BANK,
                recipientPhone,
                amount,
                currency,
                narration,
                transactionId
        );
    }
}
