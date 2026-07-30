package com.loot.controller.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StkCallbackEnvelope(@JsonProperty("Body") Body body) {

    public record Body(@JsonProperty("stkCallback") StkCallback stkCallback) {}
}
