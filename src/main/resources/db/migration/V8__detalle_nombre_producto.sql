-- Snapshot del nombre del producto al momento del pedido.
-- No edita migraciones aplicadas: agrega columna nullable para no romper
-- detalles históricos; hacia adelante siempre se escribe desde CrearPedidoUseCase.
ALTER TABLE detalle_pedido ADD COLUMN nombre_producto VARCHAR(150);
