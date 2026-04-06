-- Win / no-loss streak columns for spec-style XP bonuses
ALTER TABLE sword_of_knowledge.users
  ADD COLUMN IF NOT EXISTS win_streak INT NOT NULL DEFAULT 0;
ALTER TABLE sword_of_knowledge.users
  ADD COLUMN IF NOT EXISTS no_loss_streak INT NOT NULL DEFAULT 0;
