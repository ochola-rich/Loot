CREATE TABLE tournaments (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255)   NOT NULL,
    entry_fee_kes  NUMERIC(12,2)  NOT NULL,
    max_entries    INTEGER        NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_tournaments_status ON tournaments (status);
