-- Cuenta de mesa: el cobro conjunto de todo lo pendiente de una mesa en una sola operación.
--
-- Una cuenta agrupa los pagos (uno por pedido, pago.pedido_id sigue siendo UNIQUE) y guarda lo que
-- es de la cuenta entera: el total, lo que entregó el cliente y el vuelto, que se calcula una sola
-- vez sobre el total. Los pagos de la cuenta llevan vuelto NULL.
CREATE TABLE cuenta_mesa (
  cuenta_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  restaurante_id BIGINT NOT NULL REFERENCES restaurante(restaurante_id),
  mesa_id BIGINT NOT NULL,
  usuario_id BIGINT NOT NULL REFERENCES usuario(usuario_id),
  metodo_pago VARCHAR(20) NOT NULL,
  total DECIMAL(10,2) NOT NULL,
  monto_entregado DECIMAL(10,2),
  vuelto DECIMAL(10,2),
  referencia_externa VARCHAR(100),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  -- La mesa tiene que ser del mismo restaurante (mismo patrón que fk_pedido_mesa_restaurante, V11).
  CONSTRAINT fk_cuenta_mesa_mesa_restaurante
    FOREIGN KEY (mesa_id, restaurante_id) REFERENCES mesa (mesa_id, restaurante_id)
);

CREATE INDEX idx_cuenta_mesa_mesa ON cuenta_mesa (restaurante_id, mesa_id);

-- NULL = pago individual (POST /api/v1/pagos), el comportamiento de siempre.
ALTER TABLE pago ADD COLUMN cuenta_id BIGINT REFERENCES cuenta_mesa(cuenta_id);
CREATE INDEX idx_pago_cuenta ON pago (cuenta_id) WHERE cuenta_id IS NOT NULL;
