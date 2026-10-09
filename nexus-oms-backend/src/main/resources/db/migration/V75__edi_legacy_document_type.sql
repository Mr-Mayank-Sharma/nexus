-- V75: EDI uploads failed on every Flyway-built database.
-- nx_edi_documents still has the V8 column document_type (NOT NULL). V67 added doc_type, which
-- is what NxEdiDocument maps, and copied existing values across, but left document_type NOT
-- NULL. Every new insert wrote doc_type only and violated the constraint, so
-- POST /edi/upload returned an error for any file. Keep the legacy column for old readers,
-- drop its NOT NULL, and keep it in sync with doc_type.
ALTER TABLE nx_edi_documents ALTER COLUMN document_type DROP NOT NULL;
UPDATE nx_edi_documents SET document_type = doc_type WHERE document_type IS NULL AND doc_type IS NOT NULL;

CREATE OR REPLACE FUNCTION nx_edi_documents_sync_type() RETURNS trigger AS $$
BEGIN
    IF NEW.document_type IS NULL THEN
        NEW.document_type := NEW.doc_type;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_edi_documents_sync_type ON nx_edi_documents;
CREATE TRIGGER trg_edi_documents_sync_type
    BEFORE INSERT OR UPDATE ON nx_edi_documents
    FOR EACH ROW EXECUTE FUNCTION nx_edi_documents_sync_type();
