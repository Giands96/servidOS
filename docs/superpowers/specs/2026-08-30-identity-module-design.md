# Design Spec — Identity Module (CrearUsuario + Login) — 2026-08-30

## Contexto
Módulo identity con Usuario puro + UsuarioRestaurante (PK compartida usuario_id, 1:1 MVP) + Login JWT. Tras tenant, se replica patrón hexagonal. Seguridad JWT HttpOnly + refresh se enseña en sesión dedicada, no se automatiza ahora (deferido).

## 1. Arquitectura y Límites
```
identity/
├── domain/ Usuario + UsuarioRestaurante + UsuarioPlataforma (@Builder, crear())
├── infrastructure/jpa/ UsuarioJpaEntity + UsuarioRestauranteJpaEntity (@Entity, PK compartida) + UsuarioJpaRepository + UsuarioRestauranteJpaRepository + RolRestauranteJpaRepository
├── infrastructure/mapper/ UsuarioMapper
├── infrastructure/ JwtService + SecurityConfig (stubs deferidos)
└── application/ CrearUsuarioUseCase + LoginUseCase
```
- Usuario no conoce restaurante_id directo; vínculo via UsuarioRestaurante.
- Login genera JWT con restaurante_id + rol en claim para TenantContext.

## 2. Modelo y Flujos

### Usuario.crear(nombre, apellido, email, passwordHash)
- nombre 2-100, email regex, passwordHash != null, estado ACTIVO

### UsuarioRestaurante.crear(usuarioId, restauranteId, rolId)
- todos != null, estado ACTIVO

### CrearUsuarioUseCase.Command(nombre, apellido, email, password, rolRestauranteId) + restauranteId param (TenantContext)
```
validar: nombre/email blank antes DB, existsByEmail (global unique), restaurante exists, rol exists
hash = passwordEncoder.encode(password)
u = Usuario.crear(...hash)
savedU = usuarioRepository.save(mapper.toEntity(u))
ur = UsuarioRestaurante.crear(savedU.getUsuarioId(), restauranteId, rolId)
usuarioRestauranteRepository.save(...)
publish(UsuarioCreadoEvent)
```

### LoginUseCase.Command(email, password)
```
validar blank
u = findByEmail(lower).orElseThrow("Credenciales inválidas")
if !matches(password, hash) throw
if estado != ACTIVO throw
ur = findById(u.usuario_id).orElseThrow
if ur.estado != ACTIVO throw
token = jwtService.generate(u.usuario_id, ur.restaurante_id, ur.rol_id)
return token
```
Mensaje genérico para no filtrar existencia de email.

## 3. Persistencia y Mappers

### Entities
- UsuarioJpaEntity -> usuario (usuario_id IDENTITY, nombre 100, apellido 100, email 150 unique, password_hash 255, ultimo_acceso, estado ACTIVO/INACTIVO, created_at/updated_at @PrePersist)
- UsuarioRestauranteJpaEntity -> usuario_restaurante (usuario_id PK FK usuario, restaurante_id FK, rol_restaurante_id FK, estado, created_at/updated_at)

### Repositories
```
UsuarioJpaRepository: findByEmail, existsByEmail
UsuarioRestauranteJpaRepository: findById (PK usuario_id)
RolRestauranteJpaRepository: existsById
```

### Mappers
- UsuarioMapper toDomain/toEntity/updateEntity (nunca muta usuario_id)

### Seguridad Deferida
- JwtService / SecurityConfig quedan stubs; sesión futura enseña HttpOnly + access 15min + refresh + CSRF.

## 4. Testing — Deferido
Sin deps test.

