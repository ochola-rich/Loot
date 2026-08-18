-- AES-256-GCM ciphertext (IV + tag + payload, base64-encoded) is longer than
-- a plain E.164 phone number, and a random IV means the same phone number
-- never encrypts to the same value twice, so the equality index is no
-- longer useful for lookups.
ALTER TABLE entry_payments ALTER COLUMN player_phone TYPE VARCHAR(255);
DROP INDEX IF EXISTS idx_entry_payments_player_phone;

ALTER TABLE prize_disbursals ALTER COLUMN recipient_phone TYPE VARCHAR(255);
