# Identity Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar identity vertical slice: Usuario + UsuarioRestaurante con CrearUsuario y Login con patrón hexagonal.

**Architecture:** Domain POJOs con crear() validando con BusinessException; JpaEntities con PK compartida usuario_id; Mappers @Component; UseCases @Service @Transactional con Command records y validación tenant; JWT generation deferido (stub).

**Tech Stack:** Spring Boot, Spring Data JPA, BCrypt PasswordEncoder, Lombok, Java 17 records, BusinessException

## Global Constraints
- Monolito modular Spring Modulith — boundaries por paquete
- Dominio nunca toca JPA — solo POJOs con BusinessException
- Command usa records Java 17
- Usuario email único global, case-insensitive lower
- UsuarioRestaurante PK = usuario_id (1:1 MVP)
- Login mensaje genérico "Credenciales inválidas" para no filtrar existencia
- JwtService deferido — no implementar HttpOnly ahora, solo stub con generate method
- Testing deferido

---

### Task 1: Dominio Usuario + UsuarioRestaurante crear()

**Files:**
- Modify: `src/main/java/com/servidos/v1/identity/domain/Usuario.java`
- Modify: `src/main/java/com/servidos/v1/identity/domain/UsuarioRestaurante.java`

**Interfaces:**
- Consumes: `BusinessException`, `EstadoUsuario`
- Produces: `Usuario.crear` y `UsuarioRestaurante.crear` usadas por Task 5

- [ ] **Step 1: Actualizar Usuario.java con @Builder y crear()**

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Usuario {
    Long usuario_id; String nombre; String apellido; String email; String password_hash;
    LocalDateTime ultimo_acceso; EstadoUsuario estado; LocalDateTime created_at; LocalDateTime updated_at;
    public static Usuario crear(String nombre, String apellido, String email, String password_hash) {
        if (nombre == null || nombre.trim().isEmpty() || nombre.trim().length() < 2) throw new BusinessException("El nombre es obligatorio");
        if (email == null || email.trim().isEmpty() || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) throw new BusinessException("El email no es válido");
        if (password_hash == null || password_hash.isEmpty()) throw new BusinessException("El password es obligatorio");
        return Usuario.builder().nombre(nombre.trim()).apellido(apellido).email(email.trim().toLowerCase()).password_hash(password_hash).estado(EstadoUsuario.ACTIVO).build();
    }
}
```

- [ ] **Step 2: Actualizar UsuarioRestaurante.java con @Builder y crear()**

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UsuarioRestaurante {
    private Long usuario_id; private Long restaurante_id; private Long rol_restaurante_id; private EstadoUsuario estado; private LocalDateTime created_at; private LocalDateTime updated_at;
    public static UsuarioRestaurante crear(Long usuario_id, Long restaurante_id, Long rol_restaurante_id) {
        if (usuario_id == null) throw new BusinessException("El usuario_id es obligatorio");
        if (restaurante_id == null) throw new BusinessException("El restaurante_id es obligatorio");
        if (rol_restaurante_id == null) throw new BusinessException("El rol es obligatorio");
        return UsuarioRestaurante.builder().usuario_id(usuario_id).restaurante_id(restaurante_id).rol_restaurante_id(rol_restaurante_id).estado(EstadoUsuario.ACTIVO).build();
    }
}
```

- [ ] **Step 3: Verificar compilación**

Run: `mvn compile -DskipTests` with JAVA_HOME=C:\Users\dsg48\.jdks\corretto-26.0.1
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/servidos/v1/identity/domain/Usuario.java src/main/java/com/servidos/v1/identity/domain/UsuarioRestaurante.java
git commit -m "feat(identity): add Usuario domain crear()"
```

---

### Task 2: JPA Entities

**Files:**
- Create: `src/main/java/com/servidos/v1/identity/infrastructure/UsuarioJpaEntity.java` (if path is identity/infrastructure/jpa check)
- Create: `src/main/java/com/servidos/v1/identity/infrastructure/UsuarioRestauranteJpaEntity.java`

**Interfaces:**
- Consumes: `EstadoUsuario`
- Produces: Entities usadas por repos/mappers

(Note: actual path in project is `identity/infrastructure/UsuarioJpaRepository.java` — verify and place entities in same package as repos, i.e., `identity/infrastructure/jpa/` if exists, else `identity/infrastructure/`. Check existing files.)

- [ ] **Step 1: Crear UsuarioJpaEntity.java**

```java
@Entity @Table(name = "usuario", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Data @NoArgsConstructor @AllArgsConstructor
public class UsuarioJpaEntity {
    @Id @GeneratedValue(strategy = IDENTITY) @Column(name = "usuario_id") private Long usuarioId;
    @Column(name = "nombre", nullable = false, length = 100) private String nombre;
    @Column(name = "apellido", length = 100) private String apellido;
    @Column(name = "email", nullable = false, unique = true, length = 150) private String email;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(name = "ultimo_acceso") private LocalDateTime ultimoAcceso;
    @Enumerated(STRING) @Column(name = "estado", nullable = false, length = 20) private EstadoUsuario estado;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { var now=LocalDateTime.now(); createdAt=now; updatedAt=now; }
    @PreUpdate protected void onUpdate() { updatedAt=LocalDateTime.now(); }
}
```

- [ ] **Step 2: Crear UsuarioRestauranteJpaEntity.java**

```java
@Entity @Table(name = "usuario_restaurante")
@Data @NoArgsConstructor @AllArgsConstructor
public class UsuarioRestauranteJpaEntity {
    @Id @Column(name = "usuario_id") private Long usuarioId;
    @Column(name = "restaurante_id", nullable = false) private Long restauranteId;
    @Column(name = "rol_restaurante_id", nullable = false) private Long rolRestauranteId;
    @Enumerated(STRING) @Column(name = "estado", nullable = false, length = 20) private EstadoUsuario estado;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { var now=LocalDateTime.now(); createdAt=now; updatedAt=now; }
    @PreUpdate protected void onUpdate() { updatedAt=LocalDateTime.now(); }
}
```

- [ ] **Step 3: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/servidos/v1/identity/infrastructure/*JpaEntity.java
git commit -m "feat(identity): add Usuario JPA entities"
```

---

### Task 3: Repositories

**Files:**
- Modify: `src/main/java/com/servidos/v1/identity/infrastructure/UsuarioJpaRepository.java`
- Modify: `src/main/java/com/servidos/v1/identity/infrastructure/UsuarioRestauranteJpaRepository.java` (create if not exists)

**Interfaces:**
- Consumes: Entities Task 2
- Produces: `findByEmail`, `existsByEmail` usadas por Task 5

- [ ] **Step 1: Actualizar UsuarioJpaRepository.java**

```java
@Repository
public interface UsuarioJpaRepository extends JpaRepository<UsuarioJpaEntity, Long> {
    Optional<UsuarioJpaEntity> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

- [ ] **Step 2: Crear/Actualizar UsuarioRestauranteJpaRepository.java**

```java
@Repository
public interface UsuarioRestauranteJpaRepository extends JpaRepository<UsuarioRestauranteJpaEntity, Long> {}
```

- [ ] **Step 3: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/servidos/v1/identity/infrastructure/*Repository.java
git commit -m "feat(identity): add identity repositories"
```

---

### Task 4: Mappers

**Files:**
- Create: `src/main/java/com/servidos/v1/identity/infrastructure/mapper/UsuarioMapper.java`

**Interfaces:**
- Consumes: Domain + Entity
- Produces: toDomain/toEntity usados por Task 5

- [ ] **Step 1: Crear UsuarioMapper.java**

```java
@Component
public class UsuarioMapper {
    public Usuario toDomain(UsuarioJpaEntity e) { /* mapea todos campos */ }
    public UsuarioJpaEntity toEntity(Usuario d) { /* new + setters */ }
}
```

- [ ] **Step 2: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/servidos/v1/identity/infrastructure/mapper/UsuarioMapper.java
git commit -m "feat(identity): add usuario mapper"
```

---

### Task 5: UseCases CrearUsuario + Login

**Files:**
- Modify: `src/main/java/com/servidos/v1/identity/application/CrearUsuarioUseCase.java`
- Modify: `src/main/java/com/servidos/v1/identity/application/LoginUseCase.java`

**Interfaces:**
- Consumes: Domain crear(), Mappers, Repositories, PasswordEncoder, JwtService stub, BusinessException
- Produces: UseCases consumidos por controllers

- [ ] **Step 1: Implementar CrearUsuarioUseCase.java**

```java
@Service @RequiredArgsConstructor
public class CrearUsuarioUseCase {
    private final UsuarioJpaRepository usuarioRepository;
    private final UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    private final RestauranteJpaRepository restauranteRepository;
    private final RolRestauranteJpaRepository rolRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;
    public record Command(String nombre, String apellido, String email, String password, Long rolRestauranteId) {}
    @Transactional public Usuario ejecutar(Command cmd, Long restauranteId) { /* validar blank antes DB, existsByEmail, restaurante exists, rol exists, encode, crear, save, publish */ }
}
```

- [ ] **Step 2: Implementar LoginUseCase.java**

```java
@Service @RequiredArgsConstructor
public class LoginUseCase {
    private final UsuarioJpaRepository usuarioRepository;
    private final UsuarioRestauranteJpaRepository urRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    public record Command(String email, String password) {}
    public String ejecutar(Command cmd) { /* validar, findByEmail lower, matches, estado checks, generate token */ }
}
```

- [ ] **Step 3: Verificar compilación**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS (si falta PasswordEncoder bean, agregar @Bean BCryptPasswordEncoder en SecurityConfig stub)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/servidos/v1/identity/application/CrearUsuarioUseCase.java src/main/java/com/servidos/v1/identity/application/LoginUseCase.java
git commit -m "feat(identity): implement CrearUsuario and Login use cases"
```

---

### Task 6: Verificación final

- [ ] **Step 1: Compilar todo**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

```

