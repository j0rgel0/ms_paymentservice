-- Enable uuid-ossp extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create payments table with payment_id as UUID PRIMARY KEY with default uuid_generate_v4()
CREATE TABLE IF NOT EXISTS payments
(
    payment_id     UUID PRIMARY KEY            DEFAULT uuid_generate_v4(),
    order_id       UUID           NOT NULL,
    user_id        UUID           NOT NULL,
    amount         DECIMAL(19, 4) NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    payment_method VARCHAR(50)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    transaction_id VARCHAR(100),
    created_at     TIMESTAMP WITHOUT TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'),
    updated_at     TIMESTAMP WITHOUT TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc')
);
-- Create necessary indexes
CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments(user_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);