-- Un pedido solo puede apuntar a una mesa de su propio restaurante.
--
-- Por qué: la FK original era pedido.mesa_id -> mesa(mesa_id), sin restaurante_id.
-- La base aceptaba que un pedido del restaurante A referenciara una mesa del B.
-- La aplicación ya lo valida (CrearPedidoUseCase), esto lo garantiza también en BD.
--
-- Si existiera algún pedido cruzado, el ADD CONSTRAINT falla y la migración se
-- detiene: es preferible a dejar el dato inconsistente sin aviso.

-- 1) La FK compuesta necesita un UNIQUE sobre las columnas referenciadas.
--    mesa_id ya es PK, así que el par es único por definición.
ALTER TABLE mesa ADD CONSTRAINT uq_mesa_id_restaurante UNIQUE (mesa_id, restaurante_id);

-- 2) Reemplazar la FK simple por la compuesta. MATCH SIMPLE (default): si
--    mesa_id es NULL (delivery, para llevar) la FK no aplica.
ALTER TABLE pedido DROP CONSTRAINT pedido_mesa_id_fkey;
ALTER TABLE pedido ADD CONSTRAINT fk_pedido_mesa_restaurante
    FOREIGN KEY (mesa_id, restaurante_id) REFERENCES mesa (mesa_id, restaurante_id);
