-- Crear usuario ADMINISTRADOR de un restaurante (dev/recuperación de acceso)
-- Password: Admin123!  (hash BCrypt generado con spring-security BCryptPasswordEncoder, strength 10)
-- Ejecutar dentro de psql:  \i crear_admin.sql
-- NO es un seed de Flyway: es recuperación manual de acceso en DB existente.

\set ON_ERROR_STOP on

BEGIN;

-- 1) Definir datos (editá estas 3 variables)
--    Usa un restaurante existente. Verificá el ID con:
--      SELECT restaurante_id, slug, nombre FROM restaurante ORDER BY restaurante_id;
\set admin_email   'admin@local.test'
\set admin_nombre  'Admin'
\set rest_id       1

-- 2) Usuario (password_hash es NOT NULL y único el email)
INSERT INTO usuario (nombre, apellido, email, password_hash, estado, created_at, updated_at)
VALUES (:'admin_nombre', 'Local', :'admin_email',
        '$2a$10$1vrMAYtSsitZ.vvqzL/DkOsvLSuhg9eEXHGN8V0hyCVKyHQw9bpAi',
        'ACTIVO', now(), now())
RETURNING usuario_id;

-- 3) Membresía con rol ADMINISTRADOR (V7__roles_seed.sql ya crea ese rol)
INSERT INTO usuario_restaurante (usuario_id, restaurante_id, rol_restaurante_id, estado, created_at, updated_at)
SELECT u.usuario_id,
       :'rest_id'::bigint,
       r.rol_restaurante_id,
       'ACTIVO',
       now(), now()
FROM usuario u, rol_restaurante r
WHERE u.email = :'admin_email'
  AND r.nombre = 'ADMINISTRADOR'
RETURNING usuario_id, restaurante_id, rol_restaurante_id;

COMMIT;

-- 4) Verificación
SELECT u.usuario_id, u.email, u.estado AS usuario_estado,
       r.nombre AS rol, ur.estado AS membresia_estado, ur.restaurante_id
FROM usuario u
JOIN usuario_restaurante ur ON ur.usuario_id = u.usuario_id
JOIN rol_restaurante r      ON r.rol_restaurante_id = ur.rol_restaurante_id
WHERE u.email = :'admin_email';

-- 5) Login (fuera de psql)
-- curl.exe -X POST http://localhost:8080/api/v1/auth/login ^
--   -H "Content-Type: application/json" ^
--   -d "{\"email\":\"admin@local.test\",\"password\":\"Admin123!\"}"