---
title: Identity
date: 2026-08-30
tags:
  - servidos
  - modulo
  - identity
aliases:
  - Módulo Identity
---

# Identity — [[ServidOS - Módulos Implementados#Identity — Verde]]

> [!warning] JWT stub
> `JwtService.generate()` es `"stub-jwt"` — HttpOnly + refresh deferido para clase senior→junior.

- `Usuario` + `UsuarioRestaurante` (PK `usuario_id` 1:1)
- `CrearUsuario` (restauranteId del TenantContext) + `Login` (mensaje genérico)
- Spec: `docs/superpowers/specs/2026-08-30-identity-module-design.md`

