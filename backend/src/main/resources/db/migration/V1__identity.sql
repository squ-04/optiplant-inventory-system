CREATE TABLE identity_branch (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_identity_branch PRIMARY KEY CHECK (id > 0),
    external_id UUID NOT NULL CONSTRAINT uq_identity_branch_external UNIQUE,
    code VARCHAR(64) NOT NULL CONSTRAINT uq_identity_branch_code UNIQUE,
    name VARCHAR(160) NOT NULL CHECK (btrim(name) <> ''),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_identity_branch_code CHECK (code = btrim(code) AND code <> ''),
    CONSTRAINT ck_identity_branch_uuid CHECK (external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')
);

CREATE TABLE identity_user_account (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_identity_user_account PRIMARY KEY CHECK (id > 0),
    external_id UUID NOT NULL CONSTRAINT uq_identity_user_external UNIQUE,
    code VARCHAR(64) NOT NULL CONSTRAINT uq_identity_user_code UNIQUE CHECK (code = btrim(code) AND code <> ''),
    full_name VARCHAR(160) NOT NULL CHECK (btrim(full_name) <> ''),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN','MANAGER','OPERATOR')),
    branch_id BIGINT CONSTRAINT fk_identity_user_branch REFERENCES identity_branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_version BIGINT NOT NULL DEFAULT 0 CHECK (credentials_version >= 0),
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_identity_user_branch CHECK (role = 'ADMIN' OR branch_id IS NOT NULL),
    CONSTRAINT ck_identity_user_uuid CHECK (external_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')
);
CREATE INDEX ix_identity_user_branch_active ON identity_user_account(branch_id, active);

CREATE TABLE identity_login_guard (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_identity_login_guard PRIMARY KEY CHECK (id > 0),
    subject_hash BYTEA NOT NULL CONSTRAINT uq_identity_guard_subject UNIQUE CHECK (octet_length(subject_hash) = 32),
    window_started_at TIMESTAMPTZ NOT NULL,
    failure_count INTEGER NOT NULL DEFAULT 0 CHECK (failure_count >= 0),
    escalation_level SMALLINT NOT NULL DEFAULT 0 CHECK (escalation_level BETWEEN 0 AND 3),
    blocked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_identity_guard_blocked ON identity_login_guard(blocked_until) WHERE blocked_until IS NOT NULL;
