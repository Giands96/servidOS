-- Crear usuario ADMIN de la PLATAFORMA (SUPERADMIN/ADMIN/MODERADOR)
-- Password: Admin123!  (hash BCrypt generado con spring-security BCryptPasswordEncoder, strength 10)
-- Ejecutar dentro de psql:  \i crear_admin_plataforma.sql
-- NO es un seed de Flyway: es recuperación/acceso manual en DB existente.

\set ON_ERROR_STOP on

BEGIN;

-- 1) Definir datos (editá estas variables)
--    Roles plataforma: SUPERADMIN (máxima), ADMIN (operativa), MODERADOR (soporte).
--    Seed en V7__roles_seed.sql.
--    Jerarquía verificada en RolNombrePlataforma: SUPERADMIN(3) > ADMIN(2) > MODERADOR(1).
\set plat_email    'superadmin@local.test'
\set plat_nombre   'Super'
\set plat_rol      'SUPERADMIN'

-- 2) Usuario (password_hash NOT NULL, email UNIQUE)
--    Importante: NO debe existir fila en usuario_restaurante para este usuario_id.
--    LoginUseCase resuelve restaurante PRIMERO: si hay membresía de restaurante,
--    el usuario se autentica como tenant e ignora el rol de plataforma.
INSERT INTO usuario (nombre, apellido, email, password_hash, estado, created_at, updated_at)
VALUES (:'plat_nombre', 'Plataforma', :'plat_email',
        '$2a$10$1vrMAYtSsitZ.vvqzL/DkOsvLSuhg9eEXHGN8V0hyCVKyHQw9bpAi',
        'ACTIVO', now(), now())
ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash,
                                   estado = 'ACTIVO',
                                   updated_at = now()
RETURNING usuario_id;

-- 3) Membresía de plataforma (usuario_plataforma NO tiene restaurante_id)
INSERT INTO usuario_plataforma (usuario_id, rol_plataforma_id, estado, created_at)
SELECT u.usuario_id, r.rol_plataforma_id, 'ACTIVO', now()
FROM usuario u, rol_plataforma r
WHERE u.email = :'plat_email'
  AND r.nombre = :'plat_rol'
RETURNING usuario_id, rol_plataforma_id;

-- 4) Guardia: si este usuario tiene membresía de restaurante, el login lo va a tratar
--    como tenant y el rol de plataforma NO se aplica. Debe devolver 0 filas.
SELECT u.email, ur.restaurante_id, rr.nombre AS rol_tenant
FROM usuario u
JOIN usuario_restaurante ur ON ur.usuario_id = u.usuario_id
JOIN rol_restaurante rr      ON rr.rol_restaurante_id = ur.rol_restaurante_id
WHERE u.email = :'plat_email';

COMMIT;

-- 5) Verificación (rol_plataforma.estado debe ser 'ACTIVO', sembrado por V7)
SELECT u.usuario_id, u.email, u.estado AS usuario_estado,
       r.nombre AS rol_plataforma, r.estado AS rol_estado, up.estado AS membresia_estado
FROM usuario u
JOIN usuario_plataforma up ON up.usuario_id = u.usuario_id
JOIN rol_plataforma r      ON r.rol_plataforma_id = up.rol_plataforma_id
WHERE u.email = :'plat_email';

-- 6) Login (fuera de psql). El JWT lleva restauranteId = null y rol = SUPERADMIN.
-- curl.exe -X POST http://localhost:8080/api/v1/auth/login ^
--   -H "Content-Type: application/json" ^
--   -d "{\"email\":\"superadmin@local.test\",\"password\":\"Admin123!\"}"
--
-- 7) Probar el dashboard de plataforma (SUPERADMIN):
-- curl.exe http://localhost:8080/api/v1/restaurantes -H "Authorization: Bearer <accessToken>"