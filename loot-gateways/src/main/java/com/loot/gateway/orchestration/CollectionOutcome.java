package com.loot.gateway.orchestration;

import com.loot.gateway.CollectionResult;

/** Pairs a CollectionResult with the gateway name that actually produced it - the
 * result alone doesn't say whether it came from the primary gateway or a fallback. */
public record CollectionOutcome(CollectionResult result, String gateway) {
}
