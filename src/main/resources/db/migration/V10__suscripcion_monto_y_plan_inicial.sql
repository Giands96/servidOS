-- Snapshot del monto en la suscripción + catálogo inicial de planes.
--
-- Por qué: el precio vivía solo en plan.precio_plan, o sea que era del catálogo y
-- no del cliente. Eso hacía imposible cobrar distinto a cada restaurante y, peor,
-- cambiar el precio de lista reescribía el histórico: un cliente que pagó 29.90
-- pasaba a figurar como si hubiera pagado 39.90, sin registro de lo que pagó.
--
-- Con monto en suscripcion, cada fila recuerda qué se cobró. plan.precio_plan pasa
-- a ser el precio de lista por defecto: al renovar se copia el valor vigente, así
-- que cambiar el precio de lista sí afecta a los que renuevan (comportamiento
-- estándar en SaaS), pero las suscripciones ya emitidas conservan su monto.
--
-- moneda se guarda como snapshot (ISO 4217, 'PEN'). Hoy el catálogo es solo PEN.
--
-- No se seedea funcionalidad ni plan_funcionalidad: la decisión vigente es un único
-- plan sin feature-gating ni límites por plan. Cuando eso escale es una migration
-- nueva (ALTER TABLE plan_funcionalidad ADD COLUMN limite BIGINT NULL).

-- 1) Snapshot del monto y la moneda.
ALTER TABLE suscripcion ADD COLUMN monto DECIMAL(10,2);
ALTER TABLE suscripcion ADD COLUMN moneda CHAR(3);

-- 2) Backfill desde el precio de lista del plan que ya tenía cada suscripción.
UPDATE suscripcion s
SET monto = p.precio_plan,
    moneda = 'PEN'
FROM plan p
WHERE p.plan_id = s.plan_id;

-- 3) Cerrar a NOT NULL. DEFAULT cubre solo inserts futuros sin monto explícito.
ALTER TABLE suscripcion ALTER COLUMN monto SET NOT NULL;
ALTER TABLE suscripcion ALTER COLUMN monto SET DEFAULT 0;
ALTER TABLE suscripcion ALTER COLUMN moneda SET NOT NULL;
ALTER TABLE suscripcion ALTER COLUMN moneda SET DEFAULT 'PEN';

-- 4) Plan único inicial. Guard por NOT EXISTS porque plan.nombre_plan no tiene
--    UNIQUE y ON CONFLICT no se puede usar; Flyway serializa las migraciones, así
--    que no hay carrera entre dos ejecuciones concurrentes.
--    NOTA: la ausencia de UNIQUE en nombre_plan queda como deuda conocida. Si dos
--    planes se llaman igual, ambos aparecerán en GET /api/v1/planes.
INSERT INTO plan (nombre_plan, precio_plan, descripcion, estado, created_at)
SELECT 'Estándar', 34.90, 'Plan único vigente: acceso completo al producto, sin límites por tier.',
       'ACTIVO', now()
WHERE NOT EXISTS (SELECT 1 FROM plan WHERE nombre_plan = 'Estándar');