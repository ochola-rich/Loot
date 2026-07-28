CREATE TABLE gateway_transactions (
    id               BIGSERIAL PRIMARY KEY,
    idempotency_key  VARCHAR(64)  NOT NULL UNIQUE,
    raw_request      TEXT,
    raw_response     TEXT,
    gateway          VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
