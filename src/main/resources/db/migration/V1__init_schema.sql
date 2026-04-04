CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Document types
CREATE TYPE document_type AS ENUM ('LAW', 'CASE', 'CONSTITUTIONAL', 'INTERPRETATION');

-- Chunk types
CREATE TYPE chunk_type AS ENUM ('ARTICLE', 'SUMMARY', 'HOLDING', 'INTERPRETATION_BODY');

-- Original documents
CREATE TABLE law_documents (
    id            UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    type          document_type    NOT NULL,
    title         TEXT             NOT NULL,
    full_text     TEXT             NOT NULL,
    source_url    TEXT,
    source_id     TEXT,
    metadata      JSONB            NOT NULL DEFAULT '{}',
    enacted_date  DATE,
    last_amended  DATE,
    created_at    TIMESTAMP        NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP        NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_law_documents_source ON law_documents (type, source_id);

-- Chunked documents for search
CREATE TABLE law_chunks (
    id              UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    document_id     UUID             NOT NULL REFERENCES law_documents(id) ON DELETE CASCADE,
    content         TEXT             NOT NULL,
    chunk_type      chunk_type       NOT NULL,
    search_vector   TSVECTOR,
    embedding       vector(768),
    metadata        JSONB            NOT NULL DEFAULT '{}',
    chunk_index     INT              NOT NULL DEFAULT 0,
    created_at      TIMESTAMP        NOT NULL DEFAULT now()
);

CREATE INDEX idx_law_chunks_document ON law_chunks (document_id);
CREATE INDEX idx_law_chunks_search_vector ON law_chunks USING GIN (search_vector);
CREATE INDEX idx_law_chunks_embedding ON law_chunks USING hnsw (embedding vector_cosine_ops);

-- Auto-populate tsvector on insert/update
CREATE OR REPLACE FUNCTION update_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', NEW.content);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_law_chunks_search_vector
    BEFORE INSERT OR UPDATE OF content ON law_chunks
    FOR EACH ROW
    EXECUTE FUNCTION update_search_vector();

-- Citation relationships
CREATE TABLE citations (
    id                   UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    source_document_id   UUID NOT NULL REFERENCES law_documents(id) ON DELETE CASCADE,
    target_document_id   UUID NOT NULL REFERENCES law_documents(id) ON DELETE CASCADE,
    citation_type        TEXT NOT NULL DEFAULT 'REFERENCE',
    metadata             JSONB NOT NULL DEFAULT '{}',
    created_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_citations_source ON citations (source_document_id);
CREATE INDEX idx_citations_target ON citations (target_document_id);

-- Collection/embedding progress tracking
CREATE TABLE collection_progress (
    id              UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    task_type       TEXT             NOT NULL,
    data_type       document_type    NOT NULL,
    total_count     INT              NOT NULL DEFAULT 0,
    processed_count INT              NOT NULL DEFAULT 0,
    last_cursor     TEXT,
    status          TEXT             NOT NULL DEFAULT 'PENDING',
    error_message   TEXT,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    updated_at      TIMESTAMP        NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_collection_progress_task ON collection_progress (task_type, data_type);
