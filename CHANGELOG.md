# Changelog

Cambios relevantes del proyecto, del más reciente al más antiguo.

## 2026-10-09: tablero de cocina en vivo, listado de pedidos y limpieza de permisos

PRs: [Giands96/servidOS#13](https://github.com/Giands96/servidOS/pull/13), [Giands96/servidOS#14](https://github.com/Giands96/servidOS/pull/14) y [Giands96/servidOS#15](https://github.com/Giands96/servidOS/pull/15)

### Resumen

- La cocina puede ver los pedidos LISTOS y recibe los cambios del tablero en vivo por WebSocket.
- Nuevo listado de pedidos con filtros para caja, recepción e historial.
- RECEPCION y CAJERO pueden cobrar.
- El cambio de plan pasa a la plataforma, igual que la renovación.
- La aplicación corre en hora de Lima.
- `CAMBIOS_FRONTEND.md` resume estos cambios y los del PR #12 para el frontend.

### Reglas de negocio definidas

| Situación | Regla |
|---|---|
| Cancelar un pedido | ADMINISTRADOR o RECEPCION, sin contraseña ni aprobación; solo si no está pagado |
| Pedido pagado que hay que anular | Se reembolsa el pago, y el reembolso cancela el pedido |
| Reembolso | Solo de un pago PAGADO |
| Quién cobra | ADMINISTRADOR, RECEPCION y CAJERO |
| Cambio de plan | Solo la plataforma, cuando recibe el pago |
| Zona horaria del negocio | America/Lima |

### Cocina

- **Nuevo `GET /api/v1/cocina/listos`** (ADMINISTRADOR, RECEPCION, COCINERO): pedidos LISTO del restaurante, con el mismo formato que `GET /cocina/cola`.
- **`listoAt`** en `GET /cocina/listos` (y `null` en la cola): cuándo pasó el pedido a LISTO, para mostrar "hace N min" aunque se recargue la página. Columna nueva `pedido.listo_at` (migración V13); la entidad la fija una sola vez al llegar a LISTO, por cocina o por la API de pedidos.
- **WebSocket del tablero:** STOMP sobre WebSocket nativo en `/ws`.
  - Tópico `/topic/restaurantes/{restauranteId}/cocina`.
  - Mensaje `{pedidoId, estadoAnterior, estadoNuevo}` cada vez que un pedido entra, cambia o sale de EN_PREPARACION o LISTO.
  - Se envía después del commit: si la operación falla, no se avisa.
  - `StompAuthInterceptor`: el JWT va en el frame CONNECT (rol ADMINISTRADOR, RECEPCION o COCINERO); solo se puede suscribir al tópico del propio restaurante; el cliente no puede enviar mensajes.
- Nuevo evento `PedidoEstadoCambiadoEvent`, publicado en cada cambio de estado de `CambiarEstadoPedidoUseCase`.

### Pedidos y pagos

- **Nuevo `GET /api/v1/pedidos`** (ADMINISTRADOR, RECEPCION, CAJERO):
  - Paginado (máximo 100), del más nuevo al más viejo, solo del restaurante del token.
  - Filtros opcionales: `estado`, `fecha` (día de creación) y `porCobrar=true` (no cancelados, ni pagados, ni reembolsados).
  - Cada pedido trae `estadoPago`: PAGADO, REEMBOLSADO o null.
- **`POST /pagos`** acepta ADMINISTRADOR, RECEPCION y CAJERO (antes solo ADMINISTRADOR).
- La descripción de Swagger del reembolso estaba desactualizada (decía que el pedido quedaba cancelable); ahora explica que el reembolso lo cancela.

### Suscripciones

- **`PATCH /restaurantes/actual/plan` se reemplaza por `PATCH /restaurantes/{id}/plan`**, solo SUPERADMIN, con body `{"nuevoPlanId": n}` y sin contraseña. La nueva suscripción dura un mes, igual que la renovación (antes eran 30 días).
- Cancelar la suscripción queda solo para ADMINISTRADOR (aceptaba SUPERADMIN, que no tiene restaurante y siempre recibía 400).
- Se borra `GestionarSuscripcionUseCase.suscribir()`, que no tenía llamadas.

### Configuración

- La JVM corre en `America/Lima` (`V1Application` y surefire), para que "hoy" y `createdAt` sean los del negocio sin depender del servidor. Las fechas guardadas antes con el servidor en UTC quedan corridas 5 horas.

### Tests

- Nuevos: `CocinaWebSocketControllerTest`, `StompAuthInterceptorTest`, `ListarPedidosUseCaseTest`, `ZonaHorariaTest`, y casos nuevos en `GestionarColaCocinaUseCaseTest`, `CambiarEstadoPedidoUseCaseTest`, `RegistrarReembolsoUseCaseTest` y `PedidoControllerTest`.
- De integración (Postgres): `CocinaWebSocketIntegracionTest` (servidor y cliente STOMP reales) y `ListarPedidosIntegracionTest`. Ambos se verificaron con mutaciones: sin la validación de suscripción al tópico, o sin excluir los reembolsados de "por cobrar", fallan.
- `CambiarPlanHardenTest` reescrito para el cambio de plan de plataforma.
- `ListosConHoraIntegracionTest` (Postgres): `listoAt` por los dos caminos y que se conserva al salir de LISTO; con mutación.
- **Resultado:** 259 tests en verde.

### Pendientes

- `marcarListo` de cocina cambia el estado del pedido directamente en lugar de usar `CambiarEstadoPedidoUseCase`.
- El broker de WebSocket está en memoria: con más de una instancia del backend habría que pasar a uno externo.
- El token del WebSocket se valida solo al conectar.
- Los `@PreAuthorize` siguen sin tests (falta `spring-security-test`).

## 2026-10-08: control de acceso por suscripción, pagos y aislamiento entre restaurantes

PR: [Giands96/servidOS#12](https://github.com/Giands96/servidOS/pull/12)

### Resumen

Este cambio sale de una auditoría de seguridad y reglas de negocio. Reorganiza el control de acceso por suscripción y el flujo de pagos:

- La renovación deja de ser autoservicio con contraseña y pasa a hacerla la plataforma.
- Los reembolsos ahora cancelan el pedido.
- Se cierran varios huecos que permitían cruzar datos entre restaurantes.
- Se corrige un bug que hacía fallar siempre la creación de pedidos.

### Reglas de negocio definidas

| Situación | Regla |
|---|---|
| Restaurante sin suscripción | Solo lectura |
| Suscripción vencida o restaurante INACTIVO | Solo lectura |
| Quién renueva | Solo la plataforma (el cliente paga por billetera digital y la plataforma activa la renovación) |
| Renovar un restaurante INACTIVO | Lo deja ACTIVO |
| Cancelar la suscripción | Se aplica al terminar el período: el restaurante opera hasta `fecha_fin` |
| Renovar antes de que venza | La nueva suscripción se encadena al día siguiente de `fecha_fin`; se muestra la vigente |
| Restaurante bloqueado con pedidos en curso | Puede cerrarlos: avanzar estado, marcar listo, cobrar y reembolsar |
| Reembolso | Cancela el pedido si no se entregó, y es definitivo: no se puede volver a cobrar |
| Delivery EN_ENTREGA | Se puede cancelar si no está pagado |
| ENTREGADO sin pago | Permitido (cobro posterior) |
| Restaurante demo | Suscripción con monto 0 |
| Primera suscripción | La asigna la plataforma con el mismo endpoint de renovación, indicando el plan |

### Gestión de suscripciones

- **Se elimina la renovación autoservicio.** `RenovarSuscripcionUseCase` ya no valida contraseña: ahora es una operación exclusiva de la plataforma (rol SUPERADMIN).
- **Suscripción "actual" vs "última".** La actual es la vigente hoy (la de mayor id con `fecha_inicio <= hoy`). La última es la más reciente, aunque todavía no haya empezado. Esto permite manejar bien las renovaciones programadas.
- **Cancelar exige contraseña.** `GestionarSuscripcionUseCase` valida la contraseña del usuario antes de cancelar, igual que cambiar de plan.
- **Cancelar también cancela las renovaciones programadas.** Si no, el acceso seguiría después de `fecha_fin`.
- **Renovaciones encadenadas.** La renovación empieza el día siguiente a la `fecha_fin` de la última suscripción si esta sigue vigente, u hoy si ya venció. Dura un mes, al precio de lista del plan.
- **Primera suscripción.** El mismo endpoint de renovación la crea si se indica `planId`. En las siguientes, el plan es opcional y por defecto se usa el de la última suscripción.
- **Reactivación del restaurante.** Renovar un restaurante INACTIVO lo deja ACTIVO y publica `RestauranteEstadoCambiadoEvent`.
- **Cambiar de plan** usa la suscripción actual y cancela las programadas.
- **Restaurante demo.** Se crea con una suscripción de monto 0, en lugar del precio del plan.

### Filtro de control de acceso

- **`SubscriptionFilter` revisado.** Antes bloqueaba si la suscripción estaba CANCELADA y dejaba pasar a un restaurante sin suscripción. Ahora bloquea (responde 402 en las escrituras) si el restaurante está INACTIVO, si no tiene suscripción actual o si la suscripción venció.
- **Cancelar ya no bloquea al instante.** Cancelar significa "no renovar": el restaurante opera hasta `fecha_fin`.
- **Excepción para cerrar pedidos.** Con el restaurante bloqueado se permiten:
  - `PATCH /api/v1/pedidos/{id}/estado`
  - `POST /api/v1/pedidos/{id}/confirmar`
  - `POST /api/v1/cocina/pedidos/{id}/listo`
  - `POST /api/v1/pagos`
  - `POST /api/v1/pagos/{id}/reembolso`
- **Se elimina la ruta de renovación del tenant.** Los mensajes de bloqueo ahora indican que hay que contactar a la plataforma.

### Pedidos y pagos

- **Bug corregido: crear un pedido fallaba siempre.** `POST /pedidos` no guardaba el usuario que tomaba el pedido, y la columna `pedido.usuario_id` es NOT NULL. Ahora se registra el usuario del JWT. Los tests unitarios no lo detectaban porque mockean el repositorio; se reprodujo primero con un test de integración contra Postgres.
- **La mesa tiene que ser del mismo restaurante.** Antes se podía crear un pedido con una mesa de otro restaurante. Ahora lo valida el caso de uso y también la base de datos (migración V11).
- **Nuevo `CancelarPedidoAlReembolsarListener`.** Cancela automáticamente el pedido no entregado cuando se reembolsa su pago. Corre de forma sincrónica dentro de la transacción del reembolso: si la cancelación falla, el reembolso tampoco se guarda. Usa `CambiarEstadoPedidoUseCase`, así las reglas de transición son las mismas que en la API.
- **Reembolso definitivo.** Un pedido reembolsado no se puede volver a cobrar, y el error lo explica (antes decía, por error, "Pedido ya pagado").
- **Delivery EN_ENTREGA cancelable.** Un pedido en entrega se puede cancelar si todavía no está pagado.

### Seguridad entre restaurantes (multi-tenant)

- **Membresías filtradas por restaurante.** Crear usuario, asignar rol y eliminar usuario buscan la membresía con el restaurante del JWT (`findByUsuarioIdAndRestauranteId`). Un usuario de otro restaurante ahora recibe 400 "no pertenece a este restaurante", sin revelar que existe en otro lado.
- **Refresh token.** Al rotar el token se valida que la membresía corresponda al restaurante del token.
- **Cambiar rol.** `PATCH /usuarios/{id}/rol` ahora tiene `@PreAuthorize` (ADMINISTRADOR, SUPERADMIN, ADMIN).
- **Consultas sin uso eliminadas.** Se borraron 9 consultas derivadas que no se usaban, entre ellas algunas que no filtraban por restaurante.

### Repositorios y consultas

- **`SuscripcionJpaRepository`:**
  - Nuevo `findTopByRestauranteIdAndFechaInicioLessThanEqualOrderBySuscripcionIdDesc()` para obtener la suscripción actual.
  - `listarActualConNombrePlan()`, que usan el dashboard y el detalle de la suscripción, ahora ignora las suscripciones programadas.
  - La documentación aclara la diferencia entre suscripción "actual" y "última".
- **`RestauranteJpaRepository`:** `listarConSuscripcionActual()` también ignora las suscripciones programadas.
- **`PedidoJpaRepository`:** nuevo `existsMesaEnRestaurante()` para validar la mesa.
- **`UsuarioRestauranteJpaRepository`:** nuevo `findByUsuarioIdAndRestauranteId()`.

### Cambios en la API (contrato con el frontend)

- **Se elimina** `POST /api/v1/restaurantes/actual/suscripcion/renovar`.
- **Nuevo** `POST /api/v1/restaurantes/{id}/suscripcion/renovar` (solo SUPERADMIN). Body opcional `{"planId": n}`, obligatorio en la primera suscripción.
- `POST /api/v1/restaurantes/actual/suscripcion/cancelar` ahora exige body `{"password": "..."}` (`CancelarSuscripcionRequest`).
- `POST /api/v1/pedidos` con una mesa de otro restaurante responde 400 "La mesa no existe".
- `DELETE /usuarios/{id}` y `PATCH /usuarios/{id}/rol` con un usuario de otro restaurante responden 400 en lugar de 403.
- Los mensajes 402 dicen "contactá a la plataforma para regularizar tu suscripción" (antes pedían renovar).

### Migraciones de base de datos

- **V11:** un pedido solo puede apuntar a una mesa de su mismo restaurante (FK compuesta `mesa_id, restaurante_id`).
- **V12:** se quita el `DEFAULT 0` de `suscripcion.monto`, para que el monto sea siempre explícito.

### Configuración

- `application-dev.yaml` vuelve a leer las credenciales de `${DB_USERNAME}` y `${DB_PASSWORD}`. Un commit anterior las había dejado fijas, lo que rompía `ProfileConfigurationTests`.

### Tests

- **`RenovarSuscripcionUseCaseTest` reescrito**, con métodos auxiliares (`dadoRestaurante()`, `dadaUltima()`, `dadoPlanActivo()`) y sin mocks de contraseña.
- **`SubscriptionFilterTest` reescrito:** prueba vencimiento, falta de suscripción y la excepción para cerrar pedidos.
- **Nuevos tests unitarios:** `CancelarSuscripcionUseCaseTest`, `RefreshTokenServiceTest`, `CancelarPedidoAlReembolsarListenerTest`, `CrearRestauranteDemoTest`.
- **Nuevos tests de integración** (contra Postgres):
  - `SuscripcionActualRepositoryTest`: consultas de suscripción actual frente a programada.
  - `ReembolsoCancelaPedidoIntegracionTest`: de punta a punta, reembolso → cancelación del pedido.
  - `CrearPedidoIntegracionTest`: creación de pedidos con el usuario registrado.
- **Montos:** los tests comparan `BigDecimal` con `compareTo` en lugar de `equals`.
- **Resultado:** 211 tests en verde. Los de integración necesitan Postgres en el puerto 5433, igual que `contextLoads`.

### Pendientes

- `PATCH /restaurantes/actual/plan` sigue siendo autoservicio del restaurante y crea una suscripción cobrable. Falta decidir si pasa a la plataforma.
- `GestionarSuscripcionUseCase.suscribir()` no se usa: candidato a borrar.
- Los `@PreAuthorize` no tienen tests, porque el proyecto no incluye `spring-security-test`.
- La validación de contraseña (`exigirPassword`) está copiada en cancelar y cambiar de plan; conviene extraerla a un servicio compartido.
