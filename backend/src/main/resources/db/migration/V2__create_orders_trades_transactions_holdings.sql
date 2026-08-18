-- TradeWise V2: market orders, executions, cash movements, holdings.
-- Orders, trades and transactions are three different things, modeled as three tables:
--   orders       -> intent + lifecycle (every order, every status)
--   trades       -> executions only
--   transactions -> cash movements on the wallet

CREATE TABLE orders (
    id               UUID PRIMARY KEY,
    user_id          UUID          NOT NULL REFERENCES users (id),
    symbol           VARCHAR(20)   NOT NULL,
    side             VARCHAR(4)    NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_type       VARCHAR(10)   NOT NULL CHECK (order_type IN ('MARKET')),
    quantity         BIGINT        NOT NULL CHECK (quantity > 0),
    status           VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'EXECUTED', 'CANCELLED', 'REJECTED', 'EXPIRED')),
    executed_price   NUMERIC(19,4),
    executed_at      TIMESTAMPTZ,
    rejection_reason VARCHAR(100),
    idempotency_key  VARCHAR(64)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT ux_orders_user_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE INDEX ix_orders_user_created ON orders (user_id, created_at DESC);

CREATE TABLE trades (
    id           UUID PRIMARY KEY,
    order_id     UUID          NOT NULL REFERENCES orders (id),
    user_id      UUID          NOT NULL REFERENCES users (id),
    symbol       VARCHAR(20)   NOT NULL,
    side         VARCHAR(4)    NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity     BIGINT        NOT NULL CHECK (quantity > 0),
    price        NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    executed_at  TIMESTAMPTZ   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    created_by   UUID,
    updated_by   UUID
);

CREATE INDEX ix_trades_user_executed ON trades (user_id, executed_at DESC);

CREATE TABLE transactions (
    id                 UUID PRIMARY KEY,
    user_id            UUID          NOT NULL REFERENCES users (id),
    wallet_id          UUID          NOT NULL REFERENCES wallets (id),
    transaction_type   VARCHAR(20)   NOT NULL CHECK (transaction_type IN ('SEED', 'TRADE_DEBIT', 'TRADE_CREDIT')),
    amount             NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    balance_after      NUMERIC(19,4) NOT NULL,
    reference_order_id UUID REFERENCES orders (id),
    description        VARCHAR(200),
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    created_by         UUID,
    updated_by         UUID
);

CREATE INDEX ix_transactions_user_created ON transactions (user_id, created_at DESC);

CREATE TABLE holdings (
    id            UUID PRIMARY KEY,
    user_id       UUID          NOT NULL REFERENCES users (id),
    symbol        VARCHAR(20)   NOT NULL,
    quantity      BIGINT        NOT NULL CHECK (quantity >= 0),
    average_price NUMERIC(19,4) NOT NULL,
    realized_pnl  NUMERIC(19,4) NOT NULL DEFAULT 0,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT ux_holdings_user_symbol UNIQUE (user_id, symbol)
);
