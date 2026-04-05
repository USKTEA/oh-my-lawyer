-- Change embedding dimension from 768 to 1536 (gemini-embedding-001)
-- Drop existing HNSW index first
DROP INDEX IF EXISTS idx_law_chunks_embedding;

-- Alter column type
ALTER TABLE law_chunks ALTER COLUMN embedding TYPE vector(1536);

-- Recreate HNSW index
CREATE INDEX idx_law_chunks_embedding ON law_chunks USING hnsw (embedding vector_cosine_ops);
