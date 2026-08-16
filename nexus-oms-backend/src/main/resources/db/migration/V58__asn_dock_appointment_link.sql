ALTER TABLE nx_appointments ADD COLUMN IF NOT EXISTS asn_id UUID;
ALTER TABLE nx_appointments ADD COLUMN IF NOT EXISTS edi_document_id UUID;

CREATE INDEX IF NOT EXISTS idx_nx_appointments_asn_id ON nx_appointments (asn_id);
CREATE INDEX IF NOT EXISTS idx_nx_appointments_edi_document_id ON nx_appointments (edi_document_id);
