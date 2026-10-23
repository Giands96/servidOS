-- Gestión de mesas: cuántos comensales caben y cuántos hay sentados.
--
-- capacidad: las filas existentes se rellenan con 4 (valor neutro) y el default se quita,
-- así toda mesa nueva tiene que declarar su capacidad; el admin ajusta las existentes por API.
ALTER TABLE mesa ADD COLUMN capacidad INT NOT NULL DEFAULT 4 CHECK (capacidad > 0);
ALTER TABLE mesa ALTER COLUMN capacidad DROP DEFAULT;

-- comensales: NULL mientras la mesa está LIBRE (o la ocupó un pedido sin indicar cuántos son).
ALTER TABLE mesa ADD COLUMN comensales INT CHECK (comensales > 0);

-- Hasta hoy nada escribía mesa.estado; ahora el dominio solo conoce estos dos valores.
ALTER TABLE mesa ADD CONSTRAINT ck_mesa_estado CHECK (estado IN ('LIBRE', 'OCUPADA'));
