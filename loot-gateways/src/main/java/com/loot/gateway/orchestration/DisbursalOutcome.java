package com.loot.gateway.orchestration;

import com.loot.gateway.DisbursalResult;

/** Pairs a DisbursalResult with the gateway name that actually produced it - the
 * result alone doesn't say whether it came from the primary gateway or a fallback. */
public record DisbursalOutcome(DisbursalResult result, String gateway) {
}
