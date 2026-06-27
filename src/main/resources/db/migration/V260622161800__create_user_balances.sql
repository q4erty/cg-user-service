CREATE TABLE user_balances
(
    user_id           UUID PRIMARY KEY,
    amount            NUMERIC(15, 2) NOT NULL DEFAULT 0.00 CHECK (amount >= 0),
    currency          VARCHAR(3)     NOT NULL DEFAULT 'KZT',
    version           BIGINT         NOT NULL DEFAULT 0,
    last_operation_at TIMESTAMPTZ,
    CONSTRAINT fk_user_balances_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);
