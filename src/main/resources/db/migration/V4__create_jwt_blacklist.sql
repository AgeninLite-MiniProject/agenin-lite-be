CREATE TABLE auth_jwt_blacklist (
    blacklist_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_jti UUID UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auth_jwt_blacklist_user_id FOREIGN KEY (user_id) REFERENCES mst_users (user_id) ON DELETE CASCADE
);

CREATE INDEX idx_auth_jwt_blacklist_token_jti ON auth_jwt_blacklist (token_jti);