-- Migrate profile country storage to ISO code.
ALTER TABLE sword_of_knowledge.users
ADD COLUMN IF NOT EXISTS country_code TEXT;

UPDATE sword_of_knowledge.users
SET country_code = COALESCE(NULLIF(TRIM(country_code), ''), 'SA')
WHERE country_code IS NULL OR TRIM(country_code) = '';

ALTER TABLE sword_of_knowledge.users
ALTER COLUMN country_code SET DEFAULT 'SA';
