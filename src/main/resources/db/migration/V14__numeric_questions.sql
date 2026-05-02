-- Estimation / numeric-closest questions for claiming_question & numeric tie-breakers
CREATE TABLE IF NOT EXISTS sword_of_knowledge.numeric_questions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  text TEXT NOT NULL,
  correct_answer INT NOT NULL,
  category TEXT NOT NULL DEFAULT 'general',
  difficulty TEXT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS sword_knowledge_numeric_questions_category_active_idx
  ON sword_of_knowledge.numeric_questions (category) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS sword_knowledge_numeric_questions_is_active_idx
  ON sword_of_knowledge.numeric_questions (is_active);
