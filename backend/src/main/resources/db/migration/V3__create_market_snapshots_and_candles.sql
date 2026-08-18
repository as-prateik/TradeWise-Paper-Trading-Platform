-- TradeWise V3: persisted real market data.
--
-- Why persist at all: the upstream free tier allows only 25 requests per day.
-- Without storage every application restart would re-spend that budget, and a
-- developer restarts far more than 25 times a day. Snapshots and candles are
-- therefore written once per trading day and read from here afterwards.

CREATE TABLE market_snapshots (
    symbol         VARCHAR(20) PRIMARY KEY,
    company_name   VARCHAR(120)  NOT NULL,
    price          NUMERIC(19,4) NOT NULL,
    day_open       NUMERIC(19,4) NOT NULL,
    day_high       NUMERIC(19,4) NOT NULL,
    day_low        NUMERIC(19,4) NOT NULL,
    previous_close NUMERIC(19,4) NOT NULL,
    volume         BIGINT        NOT NULL,
    -- the real trading day the close belongs to (not the fetch time)
    trading_day    DATE          NOT NULL,
    fetched_at     TIMESTAMPTZ   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    created_by     UUID,
    updated_by     UUID
);

CREATE INDEX ix_market_snapshots_fetched_at ON market_snapshots (fetched_at);

CREATE TABLE market_candles (
    id          UUID PRIMARY KEY,
    symbol      VARCHAR(20)   NOT NULL,
    trading_day DATE          NOT NULL,
    open        NUMERIC(19,4) NOT NULL,
    high        NUMERIC(19,4) NOT NULL,
    low         NUMERIC(19,4) NOT NULL,
    close       NUMERIC(19,4) NOT NULL,
    volume      BIGINT        NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT ux_market_candles_symbol_day UNIQUE (symbol, trading_day)
);

CREATE INDEX ix_market_candles_symbol_day ON market_candles (symbol, trading_day DESC);
