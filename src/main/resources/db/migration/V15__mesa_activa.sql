-- Baja lógica de mesas: una mesa dada de baja conserva su historial de pedidos.
ALTER TABLE mesa ADD COLUMN activa BOOLEAN NOT NULL DEFAULT TRUE;

-- El número solo es único entre mesas activas, así se puede reutilizar tras una baja.
-- (uq_mesa_id_restaurante, usado por la FK de pedido, no se toca.)
ALTER TABLE mesa DROP CONSTRAINT mesa_restaurante_id_numero_key;
CREATE UNIQUE INDEX uq_mesa_restaurante_numero_activa ON mesa (restaurante_id, numero) WHERE activa;
