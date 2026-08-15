CREATE TABLE api_keys (
    id          BIGSERIAL PRIMARY KEY,
    key_hash    VARCHAR(64)  NOT NULL UNIQUE,
    active      BOOLEAN      NOT NULL DEFAULT true,
    expires_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
