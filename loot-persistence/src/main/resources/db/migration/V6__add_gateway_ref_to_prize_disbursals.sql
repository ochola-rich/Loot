ALTER TABLE prize_disbursals ADD COLUMN gateway_ref VARCHAR(64);

CREATE INDEX idx_prize_disbursals_gateway_ref ON prize_disbursals (gateway_ref);
