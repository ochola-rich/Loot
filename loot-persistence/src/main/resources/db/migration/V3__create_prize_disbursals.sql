CREATE TABLE prize_disbursals (
    id               BIGSERIAL PRIMARY KEY,
    tournament_id    BIGINT         NOT NULL REFERENCES tournaments (id),
    recipient_phone  VARCHAR(20)    NOT NULL,
    amount_kes       NUMERIC(12,2)  NOT NULL,
    gateway          VARCHAR(20)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_prize_disbursals_tournament_id ON prize_disbursals (tournament_id);
CREATE INDEX idx_prize_disbursals_status ON prize_disbursals (status);
