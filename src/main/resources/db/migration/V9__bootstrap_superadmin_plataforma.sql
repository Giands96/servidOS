-- Bootstrap idempotente del SUPERADMIN de plataforma.
--
-- Por qué existe: la API no tiene forma de crear el primer usuario de plataforma.
-- POST /api/v1/usuarios exige rol ADMINISTRADOR de restaurante y rechaza sesiones de
-- plataforma, y PATCH /{id}/rol con sesión de plataforma (la única que otorga roles de
-- plataforma) requiere una sesión de plataforma previa. Es un chicken-and-egg: sin
-- SUPERADMIN sembrado no hay bootstrap por API.
--
-- Por qué NO lleva credenciales escritas acá: una migration corre en todos los
-- ambientes, incluido prod. El email y el hash se inyectan con placeholders de Flyway
-- resueltos desde la config de Spring (ver application.yaml / application-dev.yaml /
-- application-prod.yaml). En prod el bootstrap viene apagado.
--
-- Idempotencia: las dos sentencias están guardadas por NOT EXISTS sobre
-- usuario_plataforma + rol_plataforma. Si ya hay un SUPERADMIN, no insertan nada.
-- Esto además evita el problema de LoginUseCase: el login resuelve la membresía de
-- restaurante ANTES que la de plataforma, así que este usuario nunca debe tener fila
-- en usuario_restaurante (no se inserta).

-- 1) Usuario. Solo si el bootstrap está habilitado y no existe ya un SUPERADMIN.
INSERT INTO usuario (nombre, apellido, email, password_hash, estado, created_at, updated_at)
SELECT 'Bootstrap', 'Plataforma',
       '${adminPlataformaEmail}',
       '${adminPlataformaPasswordHash}',
       'ACTIVO', now(), now()
WHERE '${adminPlataformaBootstrap}' = 'true'
  AND NOT EXISTS (
        SELECT 1
        FROM usuario_plataforma up
        JOIN rol_plataforma r ON r.rol_plataforma_id = up.rol_plataforma_id
        WHERE r.nombre = 'SUPERADMIN'
      )
ON CONFLICT (email) DO NOTHING;

-- 2) Membresía de plataforma con el rol SUPERADMIN. usuario_plataforma no tiene
--    restaurante_id: es exactamente el caso de uso sin tenant que espera LoginUseCase.
INSERT INTO usuario_plataforma (usuario_id, rol_plataforma_id, estado, created_at, updated_at)
SELECT u.usuario_id, r.rol_plataforma_id, 'ACTIVO', now(), now()
FROM usuario u
CROSS JOIN rol_plataforma r
WHERE u.email = '${adminPlataformaEmail}'
  AND r.nombre = 'SUPERADMIN'
  AND '${adminPlataformaBootstrap}' = 'true'
  AND NOT EXISTS (
        SELECT 1
        FROM usuario_plataforma up2
        JOIN rol_plataforma r2 ON r2.rol_plataforma_id = up2.rol_plataforma_id
        WHERE r2.nombre = 'SUPERADMIN'
      )
ON CONFLICT (usuario_id) DO NOTHING;
