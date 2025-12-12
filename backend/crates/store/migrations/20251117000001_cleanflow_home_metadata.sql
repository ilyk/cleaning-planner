-- CleanFlow v0.9 metadata extension
-- Adds metadata column to homes for constraints/preferences JSON

ALTER TABLE homes
ADD COLUMN IF NOT EXISTS metadata JSONB;
