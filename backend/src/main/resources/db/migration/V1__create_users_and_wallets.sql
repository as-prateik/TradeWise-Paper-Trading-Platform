-- TradeWise V1: users and wallets
-- Money convention: NUMERIC(19,4) storage, rounding HALF_UP at write boundaries.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(72)  NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    UUID,
    updated_by    UUID
);

-- Case-insensitive uniqueness: the constraint is the source of truth for duplicate emails.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

CREATE TABLE wallets (
    id         UUID PRIMARY KEY,
    user_id    UUID          NOT NULL UNIQUE REFERENCES users (id),
    balance    NUMERIC(19,4) NOT NULL,
    version    BIGINT        NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT ck_wallets_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX ix_wallets_user_id ON wallets (user_id);
