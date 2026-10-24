# servidos-front — Cliente Angular de ServidOS

Carpeta autocontenida con todo lo que Claude necesita para construir el front sin adivinar el back.

## Orden de lectura

1. **`AGENTS-FRONTEND.md`** — convenciones del front (estructura, comandos, qué no tocar).
2. **`Flujo - Recepcion Cocina.md`** — modelo de negocio: quién hace qué (RECEPCIÓN→COCINA), matriz de roles, reglas del dominio.
3. **`FRONTEND_CONTEXT.md`** — contrato API completo: auth, tenant, errores, 402, endpoints por pantalla, enums y shapes.
4. **`frontend-alineacion-seguridad.md`** — prompt de arranque (pegar como primer mensaje) para construir con seguridad desde el minuto cero.

## Regla de oro

Si el contrato y el código del backend (`src/main/...`) discrepan, manda el código y se actualiza el contrato. Nada se inventa.
