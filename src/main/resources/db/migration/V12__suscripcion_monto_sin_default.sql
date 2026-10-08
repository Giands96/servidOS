-- suscripcion.monto deja de tener DEFAULT 0.
--
-- Por qué: V10 dejó DEFAULT 0 para inserts futuros sin monto. El código Java
-- siempre lo envía (Suscripcion.crear lo exige), pero cualquier INSERT manual o
-- seed que lo olvide quedaba registrado como "se cobró 0" sin ningún error.
-- Sin default, ese INSERT falla por el NOT NULL, que es lo correcto.
--
-- moneda conserva su DEFAULT 'PEN': hoy el catálogo es de una sola moneda.
ALTER TABLE suscripcion ALTER COLUMN monto DROP DEFAULT;
