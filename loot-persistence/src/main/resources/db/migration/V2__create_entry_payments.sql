CREATE TABLE entry_payments (
    id             BIGSERIAL PRIMARY KEY,
    tournament_id  BIGINT         NOT NULL REFERENCES tournaments (id),
    player_phone   VARCHAR(20)    NOT NULL,
    amount_kes     NUMERIC(12,2)  NOT NULL,
    gateway        VARCHAR(20)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    mpesa_ref      VARCHAR(64),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_entry_payments_tournament_id ON entry_payments (tournament_id);
CREATE INDEX idx_entry_payments_player_phone ON entry_payments (player_phone);
CREATE INDEX idx_entry_payments_status ON entry_payments (status);
