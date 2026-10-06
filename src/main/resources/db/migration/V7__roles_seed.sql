-- Seed idempotente del vocabulario cerrado de roles.
-- No edita migraciones aplicadas ni borra datos: solo asegura que los
-- nombres definidos en RolNombrePlataforma / RolNombreRestaurante existan.
INSERT INTO rol_plataforma (nombre, descripcion, estado) VALUES
  ('SUPERADMIN', 'Plataforma: máxima autoridad del SaaS', 'ACTIVO'),
  ('ADMIN', 'Plataforma: administración operativa', 'ACTIVO'),
  ('MODERADOR', 'Plataforma: soporte moderado', 'ACTIVO')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO rol_restaurante (nombre, descripcion, estado) VALUES
  ('ADMINISTRADOR', 'Restaurante: máxima autoridad del tenant', 'ACTIVO'),
  ('RECEPCION', 'Restaurante: recepción y cocina', 'ACTIVO'),
  ('COCINERO', 'Restaurante: cocina', 'ACTIVO'),
  ('MESERO', 'Restaurante: atención en mesa', 'ACTIVO'),
  ('CAJERO', 'Restaurante: cobros', 'ACTIVO'),
  ('REPARTIDOR', 'Restaurante: delivery', 'ACTIVO')
ON CONFLICT (nombre) DO NOTHING;
