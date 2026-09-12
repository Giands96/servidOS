CREATE TABLE refresh_token(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(usuario_id),
    familia_id UUID NOT NULL,
    hash_token VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by BIGINT REFERENCES refresh_token(id)
);

CREATE INDEX idx_refresh_token_usuario_familia ON refresh_token(usuario_id, familia_id);