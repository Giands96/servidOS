# Cambios del backend para el frontend

Fecha: 2026-10-08, actualizado el 2026-10-11. Incluye lo mergeado en [Giands96/servidOS#12](https://github.com/Giands96/servidOS/pull/12) y lo nuevo de la rama `claude/optimistic-ramanujan-hfasob`, que todavía no está en `main`.

Base de la API: `/api/v1`. Los formatos de error no cambian: siguen siendo `ErrorResponse`, con `status`, `message` y `path`.

## Resumen

| # | Cambio | Tipo | Impacto en el front |
|---|---|---|---|
| 1 | Se elimina la renovación del tenant | ❌ Rompe | `restaurante.api.ts` → `renovar()` y `paywall.page.ts` |
| 2 | Cancelar la suscripción exige la contraseña | ❌ Rompe | La llamada a cancelar tiene que mandar un body |
| 3 | Una suscripción CANCELADA sigue operando hasta `fecha_fin` | ⚠️ Cambia la regla | `subscription.guard.ts` |
| 4 | Sin suscripción vigente = solo lectura (402) | ⚠️ Cambia la regla | Paywall y aviso de suspendido |
| 5 | Con el restaurante bloqueado se pueden cerrar los pedidos en curso | ✅ Nuevo permiso | Opcional: no esconder esas acciones |
| 6 | El reembolso cancela el pedido | ⚠️ Efecto nuevo | Refrescar el pedido después de reembolsar |
| 7 | Delivery EN_ENTREGA cancelable si no está pagado | ✅ Nuevo | Mostrar "Cancelar" en ese estado |
| 8 | Validaciones entre restaurantes (mesa, usuarios) | ⚠️ Cambian los códigos | Mensajes de error |
| 9 | `POST /pedidos` ya funciona contra la base real | 🐛 Arreglo | Ninguno |
| 10 | Nuevo `GET /cocina/listos` | ✅ Nuevo | Columna "Listos" de cocina |
| 11 | WebSocket del tablero de cocina | ✅ Nuevo | Tablero en vivo |
| 12 | Nuevo `GET /pedidos` (listado con filtros) | ✅ Nuevo | Caja (por cobrar), recepción, historial |
| 13 | RECEPCION y CAJERO pueden cobrar | ⚠️ Cambian los permisos | `permissions.rules.ts` → `pagos.registrar` |
| 14 | El cambio de plan pasa a la plataforma | ❌ Rompe | Quitar el cambio de plan del tenant |
| 15 | Fechas en hora de Lima | ⚠️ Aclaración | Mostrar `createdAt` sin convertir |
| 16 | Roles de plataforma: SUPERADMIN, ADMIN y MODERADOR | ⚠️ Cambian los permisos | Panel de plataforma según el rol |
| 17 | Período mensual con `fechaFin` inclusiva | ⚠️ Cambia la regla | Textos de vencimiento |

---

## 1. Se elimina la renovación del tenant ❌

- **Se elimina:** `POST /restaurantes/actual/suscripcion/renovar`. Ahora responde 404.
- **Nuevo, para la plataforma (SUPERADMIN, ADMIN o MODERADOR):** `POST /restaurantes/{id}/suscripcion/renovar`

Ahora la renovación la hace la plataforma: el cliente paga por billetera digital y la plataforma renueva desde su panel.

```http
POST /api/v1/restaurantes/{id}/suscripcion/renovar
Authorization: Bearer <token de plataforma>
Content-Type: application/json

{ "planId": 2 }   // opcional; obligatorio solo si el restaurante nunca tuvo suscripción
```

- Responde 200 con `SuscripcionResponse`.
- Si la suscripción actual sigue vigente, la nueva arranca el día siguiente a su `fechaFin`; si no, arranca hoy. Dura un mes calendario (ver §17).
- Si el restaurante estaba INACTIVO, queda ACTIVO.
- Errores 400:
  - "El plan es obligatorio para la primera suscripción"
  - "El plan no existe o no está activo"
  - "El restaurante no existe"

**Qué tocar en el front:**
- `features/restaurante/data/restaurante.api.ts`: quitar `renovar()`, o moverlo al panel de plataforma con la URL nueva y el `id`.
- `features/restaurante/paywall.page.ts`: el ADMINISTRADOR ya no puede renovar. Reemplazar el botón por un mensaje del estilo "Contactá a la plataforma para regularizar tu suscripción".
- `shared/pages/suspendido.page.ts`: el texto "para renovar el plan" debería decir que lo regulariza la plataforma.

## 2. Cancelar la suscripción exige la contraseña ❌

> Esto es solo para la **suscripción**. Cancelar un **pedido** no pide contraseña ni autorización del administrador (ver §7).

```http
POST /api/v1/restaurantes/actual/suscripcion/cancelar
Content-Type: application/json

{ "password": "..." }
```

- Sin body o con `password` vacío responde 400 (validación).
- Con la contraseña incorrecta también responde 400.
- Solo lo puede hacer el **ADMINISTRADOR** del restaurante (antes también aceptaba SUPERADMIN, que no tiene restaurante y siempre recibía 400).
- Cancelar significa "no renovar": **el restaurante sigue operando hasta `fechaFin`**. También se cancelan las renovaciones ya programadas.

**Qué tocar:** pedir la contraseña en un modal de confirmación antes de cancelar.

## 3. Una suscripción CANCELADA sigue operando ⚠️

Antes, CANCELADA bloqueaba al instante. Ahora el backend bloquea las escrituras (402) solo si se cumple alguna de estas condiciones:

- el restaurante está INACTIVO;
- no tiene una suscripción vigente;
- la suscripción venció (`fechaFin < hoy`).

**Qué tocar:** `core/auth/guards/subscription.guard.ts` manda al paywall cuando `estado === 'CANCELADA'`. Eso ahora bloquea de más. La condición tendría que ser:

```ts
const vencida = s.fechaFin < hoyISO(); // 'YYYY-MM-DD', comparación de strings
return vencida ? router.parseUrl(subscriptionLapsedRoute(user)) : true;
```

Con CANCELADA dentro del período conviene mostrar un aviso del tipo "Tu suscripción termina el {fechaFin}", sin bloquear.

> El 402 del backend sigue siendo la fuente de verdad: el guard es solo comodidad.

## 4. Sin suscripción vigente = solo lectura ⚠️

- Antes, un restaurante sin ninguna suscripción podía escribir. Ahora recibe 402 en cualquier escritura.
- Las lecturas (GET) siguen funcionando.
- Los mensajes de 402 ahora terminan con: "contactá a la plataforma para regularizar tu suscripción".
- `GET /restaurantes/actual/suscripcion` devuelve la suscripción **vigente hoy**, no una renovación programada a futuro. Si no hay ninguna, responde 400 "No se encontró suscripción para el restaurante". El guard ya falla abierto en ese caso; el 402 de la escritura lleva al paywall igual.

## 5. Cerrar pedidos con el restaurante bloqueado ✅

Aunque el restaurante esté bloqueado (402), estas acciones siguen permitidas para terminar los pedidos en curso:

| Método | Ruta |
|---|---|
| PATCH | `/pedidos/{id}/estado` |
| POST | `/pedidos/{id}/confirmar` |
| POST | `/cocina/pedidos/{id}/listo` |
| POST | `/pagos` |
| POST | `/pagos/{id}/reembolso` |

Crear pedidos, tocar el catálogo, gestionar usuarios, etc. siguen dando 402.

**Qué tocar (opcional):** si el front esconde todo cuando la suscripción está vencida, dejar visibles estas acciones en los pedidos existentes.

## 6. El reembolso cancela el pedido ⚠️

`POST /pagos/{id}/reembolso`:

- Si el pedido **no** estaba ENTREGADO, queda CANCELADO automáticamente.
- Si estaba ENTREGADO, no cambia.
- Un pedido reembolsado **no se puede volver a cobrar**: `POST /pagos` responde 400 "El pedido fue reembolsado, no se puede volver a cobrar".
- **Solo se reembolsa un pago PAGADO.** Un pedido sin cobrar no tiene pago que devolver. Si el pago está PENDIENTE, CANCELADO o ya REEMBOLSADO, responde 400 "Solo un pago PAGADO puede reembolsarse". Mostrar "Reembolsar" solo si el pedido tiene un pago PAGADO.

**Qué tocar:**
- Después de reembolsar, volver a pedir el pedido o actualizar su estado en la vista.
- Ocultar "Cobrar" en los pedidos reembolsados.

## 7. Cambios en las transiciones de estado del pedido ✅

**Cancelar un pedido:** `PATCH /pedidos/{id}/estado` con `{"estado": "CANCELADO"}`.

- Lo puede hacer ADMINISTRADOR o RECEPCION directamente, **sin contraseña ni aprobación del administrador**.
- La única condición es que el pedido **no esté pagado**. Si ya está pagado, responde 400 "El pedido pagado no puede cancelarse": el camino es reembolsar el pago, y el reembolso lo cancela (§6).

- **Delivery EN_ENTREGA → CANCELADO:** ahora se permite si el pedido **no está pagado**. Si está pagado, responde 400.
- **ENTREGADO sin pago:** se permite (cobro posterior). No cambia, pero queda confirmado como regla.

**Qué tocar:** mostrar "Cancelar" en un delivery EN_ENTREGA sin pago.

## 8. Validaciones entre restaurantes ⚠️

| Endpoint | Antes | Ahora |
|---|---|---|
| `POST /pedidos` con `mesaId` de otro restaurante | Se aceptaba | 400 "La mesa no existe" |
| `DELETE /usuarios/{id}` con un usuario de otro restaurante | 403 | 400 "no pertenece a este restaurante" |
| `PATCH /usuarios/{id}/rol` con un usuario de otro restaurante | 403 | 400 "no pertenece a este restaurante" |
| `PATCH /usuarios/{id}/rol` sin rol ADMINISTRADOR | 403 en el caso de uso | 403 antes de entrar (sin cambio visible) |

Si llega `mesaId` en un pedido que no es de mesa, también se valida. Si no aplica, no hay que mandarlo.

## 9. `POST /pedidos` arreglado 🐛

Crear un pedido fallaba siempre contra la base real, porque no se guardaba el usuario que lo tomaba. Ya funciona, y el pedido registra el usuario del token. No hace falta cambiar el request.

## 10. Nuevo `GET /cocina/listos` ✅

```http
GET /api/v1/cocina/listos
Roles: ADMINISTRADOR, RECEPCION, COCINERO
```

Devuelve los pedidos en estado LISTO del restaurante, con **el mismo formato que `GET /cocina/cola`**:

```json
[
  {
    "pedidoId": 20,
    "mesaId": 3,
    "tipoPedido": "MESA",
    "estado": "LISTO",
    "observacion": null,
    "total": 50.00,
    "createdAt": "2026-10-08T12:30:00",
    "listoAt": "2026-10-08T12:52:10",
    "items": [
      { "detalleId": 41, "productoId": 7, "nombreProducto": "Lomo saltado", "cantidad": 2, "observacion": null }
    ]
  }
]
```

Funciona aunque el restaurante esté bloqueado, porque es una lectura.

**`listoAt`:** el momento en que el pedido pasó a LISTO, en hora de Lima.
- Sirve para el "hace N min" de "Listos para salir", y ya no se pierde al recargar. Ya no hace falta guardar un `listoAt` local en el front.
- En `GET /cocina/cola` viene `null`.
- Los pedidos que ya estaban LISTO antes de este cambio toman la hora de su última modificación.

## 11. WebSocket del tablero de cocina ✅

El tablero (cola + listos) se actualiza solo cuando un pedido entra a la cola, pasa a listos, sale de listos (EN_ENTREGA o ENTREGADO) o se cancela desde la cola.

**Conexión:** STOMP sobre WebSocket nativo, sin SockJS.

| | |
|---|---|
| URL | `ws://<host>/ws` (en prod, `wss://`) |
| Auth | Header `Authorization: Bearer <jwt>` **en el frame CONNECT**, no en la URL |
| Roles | ADMINISTRADOR, RECEPCION, COCINERO |
| Tópico | `/topic/restaurantes/{restauranteId}/cocina` |

**Mensaje:**

```json
{ "pedidoId": 20, "estadoAnterior": "EN_PREPARACION", "estadoNuevo": "LISTO" }
```

Cómo aplicarlo:

| `estadoNuevo` | Acción en el tablero |
|---|---|
| EN_PREPARACION | Agregar a la cola (pedir el detalle a `GET /cocina/cola`) |
| LISTO | Mover de la cola a listos (o pedir `GET /cocina/listos`) |
| Cualquier otro (EN_ENTREGA, ENTREGADO, CANCELADO) | Quitar de la columna de `estadoAnterior` |

**Ejemplo con `@stomp/rx-stomp`** (`npm i @stomp/rx-stomp`):

```ts
const stomp = new RxStomp();
stomp.configure({
  brokerURL: `${wsBase}/ws`,
  connectHeaders: { Authorization: `Bearer ${session.accessToken()}` },
  reconnectDelay: 3000,
  beforeConnect: (client) => {
    // token fresco en cada reconexión
    client.configure({ connectHeaders: { Authorization: `Bearer ${session.accessToken()}` } });
  },
});
stomp.activate();

stomp.connected$.subscribe(() => this.recargarColaYListos()); // resincroniza al (re)conectar
stomp.watch(`/topic/restaurantes/${restauranteId}/cocina`)
  .subscribe((m) => this.aplicar(JSON.parse(m.body)));
```

**A tener en cuenta:**
- **Rechazos:** sin token, con un token inválido o con un rol que no ve cocina, el servidor rechaza el CONNECT. Suscribirse al tópico de otro restaurante corta la sesión.
- **Token vencido:** el token se valida solo al conectar. Al renovarlo, conviene reconectar (`stomp.deactivate()` y luego `activate()`).
- **Mensajes perdidos:** se pierden los que llegan mientras no hay conexión. Por eso, al conectar y al reconectar, siempre hay que pedir `/cocina/cola` y `/cocina/listos`.
- **Canal de solo lectura:** el cliente no puede enviar mensajes; solo escucha.
- **Desarrollo local:** `proxy.conf.json` hoy solo proxia `/api`. Hay que agregar:

  ```json
  "/ws": { "target": "http://localhost:8080", "ws": true, "secure": false }
  ```

  La alternativa es conectarse directo a `ws://localhost:8080/ws`, que ya acepta el origen `http://localhost:4200`.

## 12. Nuevo `GET /pedidos` ✅

```http
GET /api/v1/pedidos?estado=ENTREGADO&fecha=2026-10-09&porCobrar=false&page=0&size=20
Roles: ADMINISTRADOR, RECEPCION, CAJERO
```

| Parámetro | Default | Qué hace |
|---|---|---|
| `estado` | todos | Solo pedidos en ese estado |
| `fecha` | todas | Solo pedidos creados ese día (`yyyy-MM-dd`, hora de Lima) |
| `porCobrar` | `false` | `true`: solo pedidos **no cancelados que nunca se cobraron**. Excluye los pagados y los reembolsados (un reembolsado no se vuelve a cobrar) |
| `page`, `size` | `0`, `20` | Paginado; `size` entre 1 y 100 (fuera de rango: 400) |

- Siempre ordena del más nuevo al más viejo; no acepta `sort`.
- Solo trae pedidos del restaurante del token.
- Responde un `Page` de Spring, igual que `GET /productos`: `content`, `totalElements`, `totalPages`, `number`, `size`, etc.

```json
{
  "content": [
    {
      "pedidoId": 20,
      "tipoPedido": "MESA",
      "mesaId": 3,
      "estado": "ENTREGADO",
      "total": 50.00,
      "observacion": null,
      "createdAt": "2026-10-09T12:30:00",
      "estadoPago": "PAGADO"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

`estadoPago` vale `PAGADO`, `REEMBOLSADO` o `null` (sin pago).

Usos típicos:
- **Caja:** `?porCobrar=true`.
- **Historial del día:** `?fecha=<hoy>`.
- **Botones según el pago:** "Cobrar" si `estadoPago` es null y el pedido no está CANCELADO; "Reembolsar" solo si es `PAGADO` (§6).

## 13. RECEPCION y CAJERO pueden cobrar ⚠️

`POST /pagos` ahora acepta **ADMINISTRADOR, RECEPCION y CAJERO** (antes solo ADMINISTRADOR). El reembolso sigue siendo ADMINISTRADOR y RECEPCION.

**Qué tocar:**
- `core/auth/domain/permissions.rules.ts`: `'pagos.registrar': ['ADMINISTRADOR', 'RECEPCION', 'CAJERO']`.
- Con eso, RECEPCION y CAJERO ven `/caja`, que se resuelve con la ruta que ya exige `pagos.registrar`. Eso cierra la deuda "RECEPCION puede reembolsar pero no ve caja".

## 14. El cambio de plan pasa a la plataforma ❌

- **Se elimina:** `PATCH /restaurantes/actual/plan`. Ahora responde 404.
- **Nuevo, para SUPERADMIN o ADMIN:** `PATCH /restaurantes/{id}/plan`

El restaurante pide el cambio por fuera; la plataforma lo aplica cuando recibe el pago, igual que la renovación.

```http
PATCH /api/v1/restaurantes/{id}/plan
Authorization: Bearer <token SUPERADMIN o ADMIN>
Content-Type: application/json

{ "nuevoPlanId": 4 }
```

- Ya no lleva `confirmado` ni `password`; la confirmación la pide el panel de plataforma.
- Cancela la suscripción actual y las programadas, y crea una nueva desde hoy por **un mes calendario** (§17), al precio del plan.
- Responde 200 con `SuscripcionResponse`.

**Qué tocar:** quitar el formulario de cambio de plan del ADMINISTRADOR (la "doble confirmación con contraseña" de la fase Restaurante). En su lugar, mostrar el plan actual y un texto para contactar a la plataforma. El cambio va al dashboard de plataforma (fase Plataforma), para SUPERADMIN y ADMIN.

## 15. Fechas en hora de Lima ⚠️

El backend corre en `America/Lima`. Las fechas sin zona (`createdAt`, `fechaInicio`, `fechaFin`, por ejemplo `"2026-10-09T12:30:00"`) **ya están en hora de Lima**.

- Mostrarlas tal cual (hora local del restaurante). **No** agregarles `Z` ni tratarlas como UTC: se correrían 5 horas.
- "Hoy" (vencimiento de la suscripción, filtro `fecha`) también es el día de Lima.

## 16. Roles de plataforma ⚠️

Los usuarios de plataforma (sin restaurante) tienen tres roles:

- **SUPERADMIN** y **ADMIN** (un socio) tienen las mismas funciones. La única diferencia es que **solo SUPERADMIN crea ADMINs**: un ADMIN no puede crear a otro ADMIN.
- **MODERADOR:** consulta restaurantes y renueva suscripciones.

Regla de jerarquía: cada rol solo da roles **menores** al suyo y solo cambia el rol de usuarios con un rol menor. Nadie crea un SUPERADMIN ni le cambia el rol a uno.

| Endpoint | SUPERADMIN | ADMIN | MODERADOR |
|---|---|---|---|
| `POST /restaurantes` (crear) | ✅ | ✅ | ❌ |
| `GET /restaurantes`, `GET /restaurantes/{id}`, `GET /restaurantes/{id}/suscripcion` | ✅ | ✅ | ✅ |
| `POST /restaurantes/{id}/suscripcion/renovar` | ✅ | ✅ | ✅ |
| `PATCH /restaurantes/{id}/plan` | ✅ | ✅ | ❌ |
| `PATCH /restaurantes/{id}/estado` (suspender/reactivar) | ✅ | ✅ | ❌ |
| `POST /usuarios/plataforma` (nuevo) | ✅ ADMIN o MODERADOR | ✅ solo MODERADOR | ❌ |
| `PATCH /usuarios/{id}/rol` con sesión de plataforma | ✅ entre ADMIN y MODERADOR | ✅ solo a MODERADORES | ❌ |

**Antes:** ADMIN no podía ver, renovar ni suspender restaurantes, y MODERADOR no podía hacer nada.

**Nuevo `POST /usuarios/plataforma`** (SUPERADMIN o ADMIN):

```http
POST /api/v1/usuarios/plataforma
Content-Type: application/json

{ "nombre": "Ana", "apellido": "Pérez", "email": "ana@mail.com", "password": "minimo8car", "rolPlataformaId": 2 }
```

- Responde 201 con `{usuarioId, email, nombre, rolPlataformaId}`.
- Si el rol es igual o superior al de quien lo crea, responde 403 "No puedes crear un usuario con un rol igual o superior al tuyo". Por ejemplo, un ADMIN que intenta crear un ADMIN.
- Si el email ya existe, responde 409.
- Los ids de rol dependen de la base de datos: no los dejes fijos en el front.

**Cambiar el rol de plataforma:** SUPERADMIN puede pasar un usuario de ADMIN a MODERADOR y viceversa. Un ADMIN no puede tocar a otro ADMIN: responde 403 "No puedes cambiar el rol de un usuario con un rol igual o superior al tuyo".

**Qué tocar:**
- En el panel de plataforma, mostrar u ocultar cada acción según la tabla.
- En el alta de usuarios, ofrecer a un ADMIN solo la opción MODERADOR.

## 17. Período mensual con `fechaFin` inclusiva ⚠️

**`fechaFin` es el último día en que el restaurante opera.** Se bloquea al día siguiente.

| Caso | Antes | Ahora |
|---|---|---|
| Alta sin demo el 11/10 | 11/10 → 10/11 (30 días, más un día extra) | 11/10 → **10/11** (un mes) |
| Renovar desde el 11/10 | 11/10 → 11/11 (un mes y un día) | 11/10 → **10/11** |
| Renovación encadenada | Arrancaba un día más tarde cada mes | Arranca el **11/11**: la fecha de cobro no se corre |
| Demo de 7 días desde el 11/10 | 11/10 → 18/10 (8 días) | 11/10 → **17/10** (7 días) |

Las suscripciones que ya existen conservan sus fechas.

**Qué tocar:** el texto de vencimiento tiene que decir "vence el {fechaFin}" o "activo hasta el {fechaFin} inclusive". El aviso de §3 ("Tu suscripción termina el {fechaFin}") sigue siendo correcto. Si el front calcula días restantes, `fechaFin − hoy + 1` da los días de acceso que quedan, contando hoy.

---

## Checklist para el front

- [ ] Quitar `renovar()` del tenant y actualizar el paywall y el aviso de suspendido (§1).
- [ ] Pedir la contraseña al cancelar la suscripción (§2).
- [ ] Guard de suscripción: bloquear por `fechaFin` vencida, no por CANCELADA (§3).
- [ ] Aviso "Tu suscripción termina el …" para CANCELADA vigente (§3).
- [ ] Refrescar el pedido después de un reembolso y ocultar "Cobrar" si está reembolsado (§6).
- [ ] "Cancelar" en un delivery EN_ENTREGA sin pago (§7).
- [ ] Columna de listos con `GET /cocina/listos` (§10).
- [ ] Tablero en vivo con STOMP y resincronización al reconectar (§11).
- [ ] Proxy de `/ws` en `proxy.conf.json` (§11).
- [ ] Caja con `GET /pedidos?porCobrar=true` e historial con `?fecha=` (§12).
- [ ] `pagos.registrar` para ADMINISTRADOR, RECEPCION y CAJERO (§13).
- [ ] Quitar el cambio de plan del tenant; llevarlo al panel de plataforma (§14).
- [ ] Mostrar las fechas sin convertir zona (§15).
- [ ] "Listos para salir": usar `listoAt` del backend en lugar del local (§10).
- [ ] Panel de plataforma con permisos por rol; alta de usuarios (SUPERADMIN: ADMIN o MODERADOR; ADMIN: solo MODERADOR) (§16).
- [ ] Textos de vencimiento con `fechaFin` inclusiva (§17).
