-- Sesion atada a un restaurante. NULL = sesion de plataforma (SUPER_ADMIN sin tenant).
ALTER TABLE refresh_token ADD COLUMN restaurante_id BIGINT REFERENCES restaurante(restaurante_id);
CREATE INDEX idx_refresh_token_restaurante ON refresh_token(restaurante_id);
