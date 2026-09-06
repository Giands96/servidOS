-- V1__init.sql — baseline ServidOS MVP
-- Convención: enums en VARCHAR para calzar con @Enumerated(STRING) y ddl-auto: validate.
-- Nombres de enum en MAYÚSCULAS para calzar con Java.

CREATE TABLE restaurante (
  restaurante_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  slug VARCHAR(100) NOT NULL UNIQUE,
  nombre VARCHAR(150) NOT NULL,
  direccion VARCHAR(255),
  estado VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE plan (
  plan_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre_plan VARCHAR(100) NOT NULL,
  precio_plan DECIMAL(10,2) NOT NULL,
  descripcion VARCHAR(255),
  estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE funcionalidad (
  funcionalidad_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre_funcionalidad VARCHAR(100) NOT NULL,
  descripcion_funcionalidad VARCHAR(255)
);

CREATE TABLE plan_funcionalidad (
  plan_id BIGINT NOT NULL REFERENCES plan(plan_id),
  funcionalidad_id BIGINT NOT NULL REFERENCES funcionalidad(funcionalidad_id),
  PRIMARY KEY (plan_id, funcionalidad_id)
);

CREATE TABLE suscripcion (
  suscripcion_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  restaurante_id BIGINT NOT NULL REFERENCES restaurante(restaurante_id),
  plan_id BIGINT NOT NULL REFERENCES plan(plan_id),
  estado VARCHAR(20) NOT NULL,
  fecha_inicio DATE NOT NULL,
  fecha_fin DATE NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP
);
CREATE INDEX idx_suscripcion_restaurante ON suscripcion(restaurante_id);
CREATE INDEX idx_suscripcion_restaurante_created ON suscripcion(restaurante_id, created_at);

CREATE TABLE usuario (
  usuario_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  apellido VARCHAR(100),
  email VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  ultimo_acceso TIMESTAMP,
  estado VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE rol_restaurante (
  rol_restaurante_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre VARCHAR(50) NOT NULL UNIQUE,
  descripcion VARCHAR(255),
  estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO'
);

CREATE TABLE rol_plataforma (
  rol_plataforma_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre VARCHAR(50) NOT NULL UNIQUE,
  descripcion VARCHAR(255),
  estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO'
);

CREATE TABLE usuario_restaurante (
  usuario_id BIGINT PRIMARY KEY REFERENCES usuario(usuario_id),
  restaurante_id BIGINT NOT NULL REFERENCES restaurante(restaurante_id),
  rol_restaurante_id BIGINT NOT NULL REFERENCES rol_restaurante(rol_restaurante_id),
  estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP
);
CREATE INDEX idx_usuario_restaurante_rest ON usuario_restaurante(restaurante_id);

CREATE TABLE usuario_plataforma (
  usuario_id BIGINT PRIMARY KEY REFERENCES usuario(usuario_id),
  rol_plataforma_id BIGINT NOT NULL REFERENCES rol_plataforma(rol_plataforma_id),
  estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP
);

CREATE TABLE categoria (
  categoria_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  restaurante_id BIGINT NOT NULL REFERENCES restaurante(restaurante_id),
  nombre VARCHAR(100) NOT NULL,
  estado VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  UNIQUE (restaurante_id, nombre)
);
CREATE INDEX idx_categoria_rest ON categoria(restaurante_id);

CREATE TABLE producto (
  producto_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  restaurante_id BIGINT NOT NULL REFERENCES restaurante(restaurante_id),
  categoria_id BIGINT REFERENCES categoria(categoria_id),
  nombre VARCHAR(150) NOT NULL,
  descripcion VARCHAR(500),
  imagen_url VARCHAR(500),
  precio DECIMAL(10,2) NOT NULL,
  estado VARCHAR(20) NOT NULL,
  tiempo_preparacion INT,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_producto_rest ON producto(restaurante_id);
CREATE INDEX idx_producto_rest_cat ON producto(restaurante_id, categoria_id);

CREATE TABLE mesa (
  mesa_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  restaurante_id BIGINT NOT NULL REFERENCES restaurante(restaurante_id),
  numero INT NOT NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'LIBRE',
  UNIQUE (restaurante_id, numero)
);
CREATE INDEX idx_mesa_rest ON mesa(restaurante_id);

CREATE TABLE pedido (
  pedido_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  restaurante_id BIGINT NOT NULL REFERENCES restaurante(restaurante_id),
  mesa_id BIGINT REFERENCES mesa(mesa_id),
  usuario_id BIGINT NOT NULL REFERENCES usuario(usuario_id),
  tipo_pedido VARCHAR(20) NOT NULL,
  observacion VARCHAR(500),
  repartidor_nombre VARCHAR(100),
  estado VARCHAR(20) NOT NULL,
  total DECIMAL(10,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_pedido_rest ON pedido(restaurante_id);
CREATE INDEX idx_pedido_rest_estado ON pedido(restaurante_id, estado);

CREATE TABLE detalle_pedido (
  detalle_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  pedido_id BIGINT NOT NULL REFERENCES pedido(pedido_id),
  restaurante_id BIGINT NOT NULL REFERENCES restaurante(restaurante_id),
  producto_id BIGINT NOT NULL REFERENCES producto(producto_id),
  cantidad INT NOT NULL,
  precio_unitario DECIMAL(10,2) NOT NULL,
  subtotal DECIMAL(10,2) NOT NULL,
  observacion VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_detalle_rest ON detalle_pedido(restaurante_id);
CREATE INDEX idx_detalle_pedido ON detalle_pedido(pedido_id);

CREATE TABLE pago (
  pago_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  pedido_id BIGINT NOT NULL UNIQUE REFERENCES pedido(pedido_id),
  restaurante_id BIGINT NOT NULL REFERENCES restaurante(restaurante_id),
  usuario_id BIGINT NOT NULL REFERENCES usuario(usuario_id),
  metodo_pago VARCHAR(20) NOT NULL,
  monto DECIMAL(10,2) NOT NULL,
  vuelto DECIMAL(10,2),
  estado VARCHAR(20) NOT NULL,
  referencia_externa VARCHAR(100),
  fecha_pago TIMESTAMP NOT NULL DEFAULT now(),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_pago_rest ON pago(restaurante_id);
CREATE INDEX idx_pago_pedido ON pago(pedido_id);

CREATE TABLE audit_log (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  restaurante_id BIGINT NOT NULL REFERENCES restaurante(restaurante_id),
  usuario_id BIGINT NOT NULL REFERENCES usuario(usuario_id),
  accion VARCHAR(100) NOT NULL,
  entidad VARCHAR(100) NOT NULL,
  entidad_id BIGINT NOT NULL,
  fecha TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_rest_ent ON audit_log(restaurante_id, entidad, entidad_id);
