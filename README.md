# cg-user-service

[![CI Pipeline](https://github.com/q4erty/cg-user-service/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/q4erty/cg-user-service/actions)
[![codecov](https://codecov.io/gh/q4erty/cg-user-service/graph/badge.svg?token=IESOVS2W2R)](https://codecov.io/gh/q4erty/cg-user-service)

# Environment Variables

## Required (no defaults)

| Variable                  | Description                                        |
|---------------------------|----------------------------------------------------|
| `DB_USER`                 | PostgreSQL username                                |
| `DB_PASSWORD`             | PostgreSQL password                                |
| `KEYCLOAK_BACKEND_SECRET` | Keycloak client secret for service-to-service auth |
| `INTERNAL_SECRET`         | Shared secret for internal inter-service requests  |

## Optional (has defaults)

| Variable                   | Default                                                                   | Description                    |
|----------------------------|---------------------------------------------------------------------------|--------------------------------|
| `USER_SERVICE_PORT`        | `8081`                                                                    | HTTP port                      |
| `DB_HOST`                  | `localhost`                                                               | PostgreSQL host                |
| `DB_PORT`                  | `5432`                                                                    | PostgreSQL port                |
| `DB_NAME`                  | `gaming_platform`                                                         | PostgreSQL database name       |
| `DB_POOL_MAX`              | `20`                                                                      | HikariCP max pool size         |
| `DB_POOL_MIN`              | `5`                                                                       | HikariCP min idle connections  |
| `DB_POOL_TIMEOUT`          | `5000`                                                                    | Connection timeout (ms)        |
| `DB_POOL_IDLE_TIMEOUT`     | `300000`                                                                  | Idle connection timeout (ms)   |
| `DB_POOL_MAX_LIFETIME`     | `1800000`                                                                 | Max connection lifetime (ms)   |
| `REDIS_HOST`               | `localhost`                                                               | Redis host                     |
| `REDIS_PORT`               | `6379`                                                                    | Redis port                     |
| `REDIS_PASSWORD`           | _(empty)_                                                                 | Redis password                 |
| `KAFKA_BOOTSTRAP_SERVERS`  | `localhost:9092`                                                          | Kafka bootstrap servers        |
| `KEYCLOAK_ISSUER_URI`      | `http://localhost:8080/realms/cloud-gaming`                               | Keycloak JWT issuer URI        |
| `KEYCLOAK_JWK_SET_URI`     | `http://localhost:8080/realms/cloud-gaming/protocol/openid-connect/certs` | Keycloak JWK set URI           |
| `KEYCLOAK_URL`             | `http://localhost:8080`                                                   | Keycloak admin server URL      |
| `KEYCLOAK_REALM`           | `cloud-gaming`                                                            | Keycloak realm                 |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `cloud-gaming-backend`                                                    | Keycloak admin client ID       |
| `SWAGGER_CLIENT_ID`        | `swagger-ui`                                                              | Swagger UI OAuth client ID     |
| `LOG_LEVEL_APP`            | `DEBUG`                                                                   | Log level for application code |
| `LOG_LEVEL_SECURITY`       | `INFO`                                                                    | Log level for Spring Security  |
| `LOG_LEVEL_WEB`            | `INFO`                                                                    | Log level for Spring Web       |

## Local Development

Create a `.env` file in the root directory of the service (it is already added to `.gitignore`):

```env
DB_USER=user
DB_PASSWORD=password
KEYCLOAK_BACKEND_SECRET=secret
INTERNAL_SECRET=secret
```