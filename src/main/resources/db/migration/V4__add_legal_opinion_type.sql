-- Add LEGAL_OPINION to document_type and chunk_type enums
ALTER TYPE document_type ADD VALUE IF NOT EXISTS 'LEGAL_OPINION';
ALTER TYPE chunk_type ADD VALUE IF NOT EXISTS 'LEGAL_OPINION';

-- Add outdated flag for document lifecycle management
ALTER TABLE law_documents ADD COLUMN IF NOT EXISTS outdated BOOLEAN NOT NULL DEFAULT false;
