# Sales Online

E-commerce engine exposed as a versioned REST API. Java 25, Spring Boot 4.1, PostgreSQL + pgvector, Redis, Kafka.

## Run

```powershell
./scripts/gen-cert.ps1      # once: self-signed keystore in config/tls/
./mvnw spring-boot:run      # https://localhost:9443
```

Health: `https://localhost:9443/actuator/health`

## Test

```powershell
./mvnw verify
```

Integration tests use the dev servers (schema `sales_online_test`, Redis DB 1). See `docs/PLAN.md` for the design.
