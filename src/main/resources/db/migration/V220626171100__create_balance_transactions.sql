CREATE TABLE balance_transactions
(
    id              UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    user_id         UUID           NOT NULL,
    amount          NUMERIC(15, 2) NOT NULL,
    type            VARCHAR(32)    NOT NULL,
    session_id      UUID,
    payment_id      UUID,
    idempotency_key VARCHAR(255)   NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    description     VARCHAR(500),

    CONSTRAINT fk_balance_transactions_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_balance_transactions_type
        CHECK (type IN ('DEPOSIT', 'SESSION_DEBIT', 'REFUND', 'BONUS', 'ADMIN_ADJUSTMENT'))
);

CREATE INDEX idx_bt_user_id_created_at
    ON balance_transactions (user_id, created_at DESC);

CREATE UNIQUE INDEX uq_bt_idempotency_key
    ON balance_transactions (idempotency_key);

CREATE INDEX idx_bt_session_id
    ON balance_transactions (session_id)
    WHERE session_id IS NOT NULL;

CREATE INDEX idx_bt_payment_id
    ON balance_transactions (payment_id)
    WHERE payment_id IS NOT NULL;
