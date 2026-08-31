// ============================================================
// restaurant-saas — schema.dbml
// Multi-tenancy: shared database + shared schema.
// Convención de aislamiento: toda tabla de negocio que puede
// consultarse de forma independiente lleva restaurante_id propio,
// aunque sea derivable por JOIN (ver detalle_pedido / pago).
// Pegar en https://dbdiagram.io para regenerar el ER visual.
// ============================================================

// ---------------- ENUMS ----------------

enum estado_registro {
  activo
  inactivo
}

enum estado_suscripcion {
  activa
  cancelada
}

enum estado_mesa {
  libre
  ocupada
  reservada
}

enum tipo_pedido {
  delivery
  recojo
  local
}

// Propuesta basada en el BPMN (imágenes 1 y 3). Ajustar si tu
// proceso real distingue más/menos estados.
enum estado_pedido {
  pendiente_confirmacion
  confirmado
  cancelado
  en_preparacion
  listo
  entregado
}

enum metodo_pago {
  efectivo
  billetera_digital
  tarjeta
}

// Desacoplado de estado_pedido a propósito: un pedido puede estar
// "entregado" con el pago aún "pendiente" (ver rama delivery del BPMN).
enum estado_pago {
  pendiente
  pagado
  anulado
}

// ---------------- MODULE: tenant ----------------

Table restaurante {
  restaurante_id bigint [pk, increment]
  slug varchar(100) [not null, unique, note: 'identificador de ruta/subdominio del tenant']
  nombre varchar(150) [not null]
  direccion varchar(255)
  estado estado_registro [not null, default: 'activo']
  created_at timestamp [not null, default: `now()`]
  updated_at timestamp
}

Table plan {
  plan_id bigint [pk, increment]
  nombre_plan varchar(100) [not null]
  precio_plan decimal(10,2) [not null]
  descripcion varchar(255)
  estado estado_registro [not null, default: 'activo']
  created_at timestamp [not null, default: `now()`]
}

Table funcionalidad {
  funcionalidad_id bigint [pk, increment]
  nombre_funcionalidad varchar(100) [not null]
  descripcion_funcionalidad varchar(255)
}

Table plan_funcionalidad {
  plan_id bigint [ref: > plan.plan_id]
  funcionalidad_id bigint [ref: > funcionalidad.funcionalidad_id]

  indexes {
    (plan_id, funcionalidad_id) [pk]
  }

  Note: 'Aquí se resuelve "¿la facturación está activada?" del BPMN: JOIN contra la suscripción activa del restaurante y esta tabla.'
}

Table suscripcion {
  suscripcion_id bigint [pk, increment]
  restaurante_id bigint [not null, ref: > restaurante.restaurante_id]
  plan_id bigint [not null, ref: > plan.plan_id]
  estado estado_suscripcion [not null, default: 'activa']
  fecha_inicio date [not null]
  fecha_fin date [not null, note: 'referencia del ciclo; puede quedar en el pasado sin que eso cambie el estado automáticamente']
  created_at timestamp [not null, default: `now()`]
  updated_at timestamp

  indexes {
    restaurante_id
    (restaurante_id, created_at)
  }

  Note: 'Días restantes/vencidos = fecha_fin - hoy, calculado al leer, nunca almacenado (puede ser negativo indefinidamente). El acceso solo se corta cuando el admin pone estado=cancelada en la fila más reciente — nunca automáticamente por fecha. "Reiniciar" tras deshabilitar = INSERTAR fila nueva (fecha_inicio=hoy, fecha_fin=hoy+duración del plan, estado=activa), no sobrescribir la anterior: conserva el historial de periodos y cancelaciones.'
}

// ---------------- MODULE: identity ----------------

Table usuario {
  usuario_id bigint [pk, increment]
  nombre varchar(100) [not null]
  apellido varchar(100)
  email varchar(150) [not null, unique, note: 'único a nivel plataforma: el login es global, el contexto se resuelve después']
  password_hash varchar(255) [not null]
  ultimo_acceso timestamp
  estado estado_registro [not null, default: 'activo']
  created_at timestamp [not null, default: `now()`]
  updated_at timestamp

  Note: 'Identidad pura: credenciales y datos personales. NO conoce restaurante ni rol directamente.'
}

Table rol_restaurante {
  rol_restaurante_id bigint [pk, increment]
  nombre varchar(50) [not null, unique]
  descripcion varchar(255)
  estado estado_registro [not null, default: 'activo']
}

Table rol_plataforma {
  rol_plataforma_id bigint [pk, increment]
  nombre varchar(50) [not null, unique]
  descripcion varchar(255)
  estado estado_registro [not null, default: 'activo']
}

// Patrón: tabla-extensión con PK compartida (joined-table inheritance).
// usuario = "superclase" (identidad). usuario_restaurante / usuario_plataforma
// = "subtipos" mutuamente excluyentes por regla de negocio.
Table usuario_restaurante {
  usuario_id bigint [pk, ref: - usuario.usuario_id]
  restaurante_id bigint [not null, ref: > restaurante.restaurante_id]
  rol_restaurante_id bigint [not null, ref: > rol_restaurante.rol_restaurante_id]
  estado estado_registro [not null, default: 'activo']
  created_at timestamp [not null, default: `now()`]
  updated_at timestamp

  indexes {
    restaurante_id
  }

  Note: 'PK simple = usuario_id (no compuesta) => fuerza 1 usuario : máx 1 restaurante hoy. estado permite revocar acceso (ej: mesero que renuncia) sin borrar el historial.'
}

Table usuario_plataforma {
  usuario_id bigint [pk, ref: - usuario.usuario_id]
  rol_plataforma_id bigint [not null, ref: > rol_plataforma.rol_plataforma_id]
  estado estado_registro [not null, default: 'activo']
  created_at timestamp [not null, default: `now()`]
  updated_at timestamp

  Note: 'Usuarios internos del SaaS (admin global, socios, soporte). No debería coexistir con una fila en usuario_restaurante para el mismo usuario_id — regla de negocio a validar en el service de creación de usuarios, la FK no lo impide por sí sola.'
}

// ---------------- MODULE: catalog ----------------

Table categoria {
  categoria_id bigint [pk, increment]
  restaurante_id bigint [not null, ref: > restaurante.restaurante_id]
  nombre varchar(100) [not null]
  estado estado_registro [not null, default: 'activo']
  created_at timestamp [not null, default: `now()`]

  indexes {
    restaurante_id
  }
}

Table producto {
  producto_id bigint [pk, increment]
  restaurante_id bigint [not null, ref: > restaurante.restaurante_id]
  categoria_id bigint [ref: > categoria.categoria_id, note: 'nullable: producto sin categorizar es válido']
  nombre varchar(150) [not null]
  descripcion text
  imagen_url varchar(500)
  precio decimal(10,2) [not null]
  tiempo_preparacion int [note: 'minutos estimados, nullable - extra añadido para gestión de cocina']
  estado estado_registro [not null, default: 'activo']
  created_at timestamp [not null, default: `now()`]
  updated_at timestamp

  indexes {
    restaurante_id
    (restaurante_id, categoria_id)
  }
}

// ---------------- MODULE: ordering ----------------

Table mesa {
  mesa_id bigint [pk, increment]
  restaurante_id bigint [not null, ref: > restaurante.restaurante_id]
  numero int [not null]
  estado estado_mesa [not null, default: 'libre']

  indexes {
    restaurante_id
    (restaurante_id, numero) [unique, note: 'evita dos mesas con el mismo número en un mismo restaurante']
  }
}

Table pedido {
  pedido_id bigint [pk, increment]
  restaurante_id bigint [not null, ref: > restaurante.restaurante_id]
  mesa_id bigint [ref: > mesa.mesa_id, note: 'nullable: delivery/recojo no usan mesa']
  usuario_id bigint [not null, ref: > usuario.usuario_id, note: 'quién registró el pedido']
  tipo_pedido tipo_pedido [not null]
  observacion text
  repartidor_nombre varchar(100) [note: 'MVP: texto libre, no FK. Evolucionar a entidad propia si se necesita historial/performance del repartidor.']
  estado estado_pedido [not null, default: 'pendiente_confirmacion']
  total decimal(10,2) [not null, default: 0]
  //fecha_entrega timestamp [note: 'se setea al "registrar entrega en el sistema"; null mientras no se entrega']
  created_at timestamp [not null, default: `now()`]
  updated_at timestamp

  indexes {
    restaurante_id
    (restaurante_id, estado) [note: 'cubre la query del tablero Kanban: pedidos de MI restaurante en estado X']
  }
}

Table detalle_pedido {
  detalle_id bigint [pk, increment]
  pedido_id bigint [not null, ref: > pedido.pedido_id]
  restaurante_id bigint [not null, ref: > restaurante.restaurante_id, note: 'denormalizado a propósito, mismo criterio que pago: evita depender de un JOIN correcto para aislar por tenant']
  producto_id bigint [not null, ref: > producto.producto_id]
  cantidad int [not null]
  precio_unitario decimal(10,2) [not null]
  subtotal decimal(10,2) [not null]
  observacion varchar(255)
  created_at timestamp [not null, default: `now()`]

  indexes {
    restaurante_id
    pedido_id
  }
}

// ---------------- MODULE: payment ----------------

Table pago {
  pago_id bigint [pk, increment]
  pedido_id bigint [not null, ref: > pedido.pedido_id]
  restaurante_id bigint [not null, ref: > restaurante.restaurante_id]
  usuario_id bigint [not null, ref: > usuario.usuario_id, note: 'quién cobró']
  metodo_pago metodo_pago [not null]
  monto decimal(10,2) [not null]
  vuelto decimal(10,2)
  estado estado_pago [not null, default: 'pendiente']
  fecha_pago timestamp
  created_at timestamp [not null, default: `now()`]
  updated_at timestamp

  indexes {
    restaurante_id
    pedido_id
  }

  Note: 'Facturación/comprobante (boleta, factura, envío a sistema de facturación) queda fuera del MVP a propósito. Cuando se construya: agregar columnas nullable aquí es un ALTER TABLE de bajo costo, no requiere migrar datos existentes.'
}

// ---------------- MODULE: shared ----------------

Table audit_log {
  id bigint [pk, increment]
  restaurante_id bigint [not null, ref: > restaurante.restaurante_id]
  usuario_id bigint [not null, ref: > usuario.usuario_id]
  accion varchar(100) [not null]
  entidad varchar(100) [not null]
  entidad_id bigint [not null]
  fecha timestamp [not null, default: `now()`]

  indexes {
    (restaurante_id, entidad, entidad_id)
  }

  Note: 'Feature gateada por funcionalidad de plan. La capa de aplicación decide si escribe aquí, verificando plan_funcionalidad antes del insert — no hay gate a nivel de esquema.'
}
