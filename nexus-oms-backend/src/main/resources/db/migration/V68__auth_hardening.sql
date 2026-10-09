-- V68: Authentication hardening
--
-- 1. nx_auth_tokens   — short-lived, single-use auth artefacts (MFA challenges, password
--                       reset links, SSO state, SSO exchange codes). Stored as SHA-256
--                       hashes so a DB read never yields a usable token, and shared by
--                       every replica (replaces per-JVM HashMaps).
-- 2. nx_user_identities — external IdP identities keyed by (provider, subject). SSO users
--                       are resolved by the IdP's stable `sub`, never by e-mail local-part.
-- 3. nx_users MFA columns — per-user RFC 6238 TOTP secret (encrypted by CredentialVault)
--                       and hashed recovery codes.

CREATE TABLE IF NOT EXISTS nx_auth_tokens (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    token_type   VARCHAR(32)  NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    username     VARCHAR(255),
    payload      TEXT,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    expires_at   TIMESTAMP    NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_auth_tokens_type_hash ON nx_auth_tokens (token_type, token_hash);
CREATE INDEX IF NOT EXISTS ix_auth_tokens_expires ON nx_auth_tokens (expires_at);

CREATE TABLE IF NOT EXISTS nx_user_identities (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES nx_users (id) ON DELETE CASCADE,
    tenant_id   UUID,
    provider    VARCHAR(32)  NOT NULL,
    subject     VARCHAR(255) NOT NULL,
    email       VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_identities_provider_subject ON nx_user_identities (provider, subject);
CREATE INDEX IF NOT EXISTS ix_user_identities_user ON nx_user_identities (user_id);

ALTER TABLE nx_users ADD COLUMN IF NOT EXISTS mfa_enabled        BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE nx_users ADD COLUMN IF NOT EXISTS mfa_secret         TEXT;
ALTER TABLE nx_users ADD COLUMN IF NOT EXISTS mfa_recovery_codes TEXT;

-- Password-reset lookups by e-mail used to scan every user in Java.
CREATE INDEX IF NOT EXISTS ix_users_email_lower ON nx_users (lower(email));
