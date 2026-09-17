-- Reembolso total: auditoria de quien y por que. NULL = pago no reembolsado.
ALTER TABLE pago ADD COLUMN reembolso_motivo VARCHAR(500);
ALTER TABLE pago ADD COLUMN reembolso_usuario_id BIGINT REFERENCES usuario(usuario_id);
