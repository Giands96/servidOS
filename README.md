# ServidOS backend

Spring Boot backend. P0.1 separates configuration and checks production secrets;
JWT authentication is still a stub and this is **not ready for public deployment**.

## Run locally

Requires a JDK supporting Java 25 and Docker with Linux containers. Run commands
from the repository root (PowerShell examples; use `./mvnw` on Unix).

1. Copy `.env.example` to `.env` **only if `.env` does not already exist**.
   Fill in `DB_USERNAME` and `DB_PASSWORD` with local-only credentials.
2. Start PostgreSQL, explicitly selecting the root environment file:

   ```powershell
   docker compose --env-file .env -f Docker/docker-compose.yaml up -d --wait
   ```

3. Start the backend:

   ```powershell
   .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
   ```

`dev` imports `${user.dir}/.env` through Spring's native `.properties` loader;
no dotenv library or shell export is needed. Set the IDE working directory to
the repository root and activate `dev` explicitly. Environment variables override
the file. The import is optional so an environment-only launch also works.

Use the shared syntax supported by Spring properties and Compose: unquoted
`KEY=value`, no `export`, no inline comments, and no backslashes or dollar signs
in values. Prefer generated hexadecimal secrets. Do not put profile selection in
`.env`: the profile must already be active before the file is imported.

Compose provisions the `servidos` database. If you change `DB_PORT`, also change
the port in `DB_URL`. PostgreSQL initialization variables only apply to a new
volume: changing `.env` does not change an existing database user's password.
Do not delete an existing volume to resolve a credential mismatch.

## Production configuration

Activate `prod` externally (`SPRING_PROFILES_ACTIVE=prod` or
`--spring.profiles.active=prod`) and supply `DB_USERNAME`, `DB_PASSWORD`, and
`JWT_SECRET` through the deployment environment/secret manager. `DB_URL` defaults
to `jdbc:postgresql://localhost:5432/servidos`; set it for a remote database.

Missing or blank required values stop startup **before datasource initialization**,
with variable names only, never secret values. `prod` does not import `.env` and
cannot be combined with `dev` or `test`. SQL output and debug mode are disabled.
As with other Spring properties, explicit deployment overrides take precedence.

P0.1 checks that `JWT_SECRET` exists; it does not consume it or issue real tokens.
Generate at least 32 random bytes for the future key. Key encoding, cryptographic
strength validation, and `AuthProperties` belong to P1.1.

## Verify safely

Configuration tests do not connect to a database or read your `.env`:

```powershell
.\mvnw.cmd '-Dtest=ProfileConfigurationTests' test
```

The full context test activates `test`, whose configuration exists only on the
test classpath. It requires `TEST_DB_URL`, `TEST_DB_USERNAME`, and
`TEST_DB_PASSWORD`; there is no fallback to your development database. Flyway
migrations run and Hibernate validates the schema, so use a disposable database:

```powershell
docker run --detach --rm --name servidos-config-test `
  -e POSTGRES_DB=servidos_test -e POSTGRES_USER=servidos_test `
  -e POSTGRES_PASSWORD=disposable-test-only `
  -p 127.0.0.1::5432 postgres:16-alpine
# Wait until this returns "accepting connections":
docker exec servidos-config-test pg_isready -U servidos_test -d servidos_test
$endpoint = docker port servidos-config-test 5432
$env:TEST_DB_URL = "jdbc:postgresql://${endpoint}/servidos_test"
$env:TEST_DB_USERNAME = 'servidos_test'
$env:TEST_DB_PASSWORD = 'disposable-test-only'
try {
  .\mvnw.cmd clean verify
} finally {
  docker stop servidos-config-test
  Remove-Item Env:TEST_DB_URL, Env:TEST_DB_USERNAME, Env:TEST_DB_PASSWORD
}
```

Use a unique container name if that name is already in use. This container has no
named volume and is removed on stop. Do not set `SPRING_DATASOURCE_*` overrides
for the test run, since Spring gives them precedence over profile files.

This step adds no authentication endpoints, JWT signing, migrations, or CI.
