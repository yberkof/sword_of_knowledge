-- Drop legacy emoji country field. Country now stored only as ISO code.
ALTER TABLE sword_of_knowledge.users
DROP COLUMN IF EXISTS country_flag;
