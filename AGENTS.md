# AGENTS.md - ServidOS

## 1. Proposito y arquitectura
API Rest multi-tenant para un sistema de gestión de restaurantes, aplicando una Arquitectura DDD/Clean Architecture: domain -> application -> infrastructure. Backend Spring Boot, Angular consumiendo la API.

## 2. Stack
- Backend: Java 25 / Spring Boot 4.1
- Frontend: Angular 22
- DB: PostgreSQL
- Infraestructura: Docker

# 3. Comandos

## Setup
`./mvnw clean install`

## Test (específico, no genérico)

`./mvnw test -Dtest=OrderServiceTest`

## Run local

`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

# 4. Estructura de carpetas

    /api    -> Controladores
    /application -> Comands | UseCase
    /domain -> Entidades 
    /infrastructure -> Repositorios JPA, JPA Entity, Mappers

# 5. Convencion de codigo

    // Casos de uso: verbo + entidad, sufijo UseCase
    public class CreateOrderUseCase


# 6. Qué NO TOCAR | PROHIBIDO

- **migrations/** ya aplicadas (crear una nueva, nunca editar una vieja)
- Modulo **legacy-billing/** (congelado, sin refactors)
- Nunca commitear **.env** ni secrets

# 7. Git / PR
- Commits: Conventional Commits (feat:, fix:, chore:)
- Squash merge únicamente
- PR necesita: tests en verde + 1 aprobación


# 8 Antes de hacer código

- Verificar que el proyecto esté en buen estado antes de empezar
- Valida archivos rotos o incompletos
- Si algo falla para, no empieces a trabajar si el sistema está roto.
- Crear plan para la funcionalidad a implementar, definir los pasos y el resultado esperado.

# 9 Después de escribir código:

- Explicar como crear un test para la funcionalidad implementada
- Explicar beneficios y trade-off (si es que hay) del código implementado.
- Mencionar los siguientes estados | pendientes a realizar
- En la carpeta docs crear en la carpeta /progress y ahi guardarás el progreso de lo que se está haciendo. Resultado de cada paso, porqué se tomaron ciertas decisiones, archivos que tocaste. 