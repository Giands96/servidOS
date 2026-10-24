# AGENTS.md - ServidOS Frontend

## 0. Flujo por defecto - ODD v3 (Organic Driven Development)
- La sobreingeniería tiene que estar justificada.
- Si el pedido es simple, se hace de una.
- Si hay incertidumbre, investigar y preguntar antes de tocar nada.
- Si es grande, armar documento de feature y trabajar por tareas, con criterios de aceptación y evidencia de que funciona.
- El proceso escala con el pedido, no al revés.

## 1. Proposito y arquitectura
Cliente Angular del sistema ServidOS (multi-tenant restaurantes). Consume `POST /api/v1` del backend. Estructura modular por dominio, sin mezclar servicios y vista.

## 2. Stack
- Frontend: Angular 22 (standalone, signals)
- Tests: Vitest/Jest para lógica, Playwright solo si el usuario lo pide
- Estilos: regirse al diseño que proporcionará el usuario. No inventar UI.
- API: ver `FRONTEND_CONTEXT.md` (contrato). Swagger dev como referencia.
- Modelo de negocio: ver `Flujo - Recepcion Cocina.md` (flujo RECEPCIÓN→COCINA, matriz de roles). Manda sobre cualquier suposición de UX.

## 3. Comandos

## Setup
`npm ci`

## Test (específico, no genérico)
`npx ng test --include='**/domain/*.spec.ts'`
`npx ng test --include='**/data/*.spec.ts'`

## Run local
`npm start` (proxy a `http://localhost:8080/api/v1`, `withCredentials: true`)

## 4. Estructura de carpetas (modular por dominio)

    /core                 -> singletons: auth, http interceptor, guards (auth, role, subscription 402)
    /features/auth        -> login, refresh-retry, session.store
    /features/cocina      -> cola EN_PREPARACION/LISTO
    /features/pedidos     -> crear, confirmar, cambiar-estado
    /features/pagos       -> registrar, reembolso
    /features/catalogo    -> productos, categorias
    /features/restaurante -> actual, plan, suscripcion/renovar
    /features/usuarios    -> registro, rol, eliminar
    Cada feature: /data (*.api, fetch testeable) + /domain (*.rules puras con test) + *.page | *.component (solo orquestan)
    /shared               -> UI dumb, sin tests
    /testing              -> mock-api, builders, fixtures

## 5. Convencion de codigo

    // Servicios de datos: <dominio>.api.ts
    // Reglas puras: <dominio>.rules.ts
    // Páginas: <dominio>.page.ts (sin lógica de negocio)

## 6. Qué NO TOCAR | PROHIBIDO

- No testear componentes pequeños ni temas visuales (solo funciones complejas).
- No inventar diseño UI ni flujos de negocio sin preguntar al usuario.
- No mezclar servicios con controllers: `*.service` / `*.api` nunca importan vista ni Router directo (salvo `core/http`).
- Nunca commitear `.env` ni tokens. Nunca pegar JWT reales en docs/tests.
- No agregar dependencias sin aprobación (UI kits, state libs, etc.).

## 7. Git / PR
- Commits: Conventional Commits (feat:, fix:, chore:)
- Squash merge únicamente
- PR necesita: tests en verde + 1 aprobación

## 8. Antes de hacer código

- Verificar que el proyecto esté en buen estado antes de empezar
- Valida archivos rotos o incompletos
- Si algo falla para, no empieces a trabajar si el sistema está roto.
- Crear plan para la funcionalidad a implementar, definir los pasos y el resultado esperado.
- Si es negocio (roles, paywall 402, cocina EN_PREPARACION/LISTO, pagos), preguntar al usuario primero.

## 9. Después de escribir código:

- Explicar como crear un test solo si es función compleja (interceptor refresh-retry, guard 402, cocina.service, validación MESA/monto).
- Explicar beneficios y trade-off (si es que hay) del código implementado.
- Mencionar los siguientes estados | pendientes a realizar
- En la carpeta docs crear en la carpeta /progress y ahi guardarás el progreso de lo que se está haciendo. Resultado de cada paso, porqué se tomaron ciertas decisiones, archivos que tocaste.
