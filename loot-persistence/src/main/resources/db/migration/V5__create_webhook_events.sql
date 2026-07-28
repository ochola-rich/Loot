CREATE TABLE webhook_events (
    id             BIGSERIAL PRIMARY KEY,
    gateway        VARCHAR(20)  NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    request_body   TEXT,
    response_body  TEXT,
    processed_at   TIMESTAMPTZ,
    status         VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_events_gateway_event_type ON webhook_events (gateway, event_type);
