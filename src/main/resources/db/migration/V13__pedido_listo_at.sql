-- Momento en que el pedido pasó a LISTO: cocina muestra "hace N min" en "Listos para salir".
ALTER TABLE pedido ADD COLUMN listo_at TIMESTAMP;

-- Los que ya están LISTO no se volvieron a tocar desde que llegaron a ese estado.
UPDATE pedido SET listo_at = updated_at WHERE estado = 'LISTO';
