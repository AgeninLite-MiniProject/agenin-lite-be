-- Baseline schema per DBML (Agenin ERD v3)
-- Updated with mentor revisions: multi-product transactions, explicit IDs, admin role.
-- Updated to use UUIDs for all Primary Keys and Foreign Keys.

CREATE TABLE mst_users (
    user_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(20)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    user_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(100) UNIQUE,
    referred_by     UUID,
    referral_code   VARCHAR(10)  UNIQUE,
    role            VARCHAR(20)  NOT NULL DEFAULT 'AGENT',
    user_status     VARCHAR(20)  NOT NULL DEFAULT 'PASSIVE',
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT fk_users_referred_by FOREIGN KEY (referred_by) REFERENCES mst_users(user_id)
);

CREATE TABLE mst_products (
    product_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_name    VARCHAR(100)   NOT NULL,
    cost_price      NUMERIC(19,2)  NOT NULL,
    selling_price   NUMERIC(19,2)  NOT NULL,
    agent_fee       NUMERIC(5,2)   NOT NULL,
    super_agent_fee NUMERIC(5,2)   NOT NULL,
    product_status  VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE TABLE trx_invitations (
    invitation_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inviter_id        UUID         NOT NULL REFERENCES mst_users(user_id),
    invitee_id        UUID         NOT NULL REFERENCES mst_users(user_id),
    invitation_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    responded_at      TIMESTAMP,
    cancelled_at      TIMESTAMP,
    CONSTRAINT uq_invitations_users UNIQUE (inviter_id, invitee_id)
);

CREATE TABLE trx_transactions (
    trx_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL REFERENCES mst_users(user_id),
    total_amount  NUMERIC(19,2) NOT NULL,
    total_profit  NUMERIC(19,2) NOT NULL,
    trx_status    VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    description   VARCHAR(255),
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    completed_at  TIMESTAMP
);

CREATE TABLE trx_items (
    item_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trx_id       UUID          NOT NULL REFERENCES trx_transactions(trx_id),
    product_id   UUID          NOT NULL REFERENCES mst_products(product_id),
    quantity     INTEGER       NOT NULL,
    item_amount  NUMERIC(19,2) NOT NULL,
    profit       NUMERIC(19,2) NOT NULL,
    CONSTRAINT uq_trx_items_product UNIQUE (trx_id, product_id)
);

CREATE TABLE trx_commissions (
    commission_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id           UUID          NOT NULL REFERENCES trx_items(item_id),
    beneficiary_id    UUID          NOT NULL REFERENCES mst_users(user_id),
    source_user_id    UUID          NOT NULL REFERENCES mst_users(user_id),
    commission_type   VARCHAR(20)   NOT NULL,
    fee_percentage    NUMERIC(5,2)  NOT NULL,
    commission_amount NUMERIC(19,2) NOT NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uq_commissions_idempotency UNIQUE (item_id, beneficiary_id, commission_type)
);

CREATE TABLE auth_refresh_tokens (
    refresh_token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES mst_users(user_id),
    token_id         VARCHAR(64)  NOT NULL UNIQUE,
    token_hash       VARCHAR(255) NOT NULL,
    expires_at       TIMESTAMP    NOT NULL,
    revoked_at       TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE sys_audit_logs (
    audit_log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id     UUID REFERENCES mst_users(user_id),
    action       VARCHAR(50)  NOT NULL,
    entity_type  VARCHAR(50)  NOT NULL,
    entity_id    UUID,
    payload      JSONB,
    ip_address   VARCHAR(45),
    user_agent   VARCHAR(255),
    audit_status VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);
