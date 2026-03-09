# keycloak-demo

Spring Boot 4 REST API secured with Keycloak via Spring Security 7 OAuth2 Resource Server (JWT).

---

## Architecture

```
┌─────────────────┐     (1) POST /token      ┌──────────────────────┐
│  Client (curl/  │ ──────────────────────── │   Keycloak 24        │
│  Postman/SPA)   │ ◄─────────────────────── │   :8180              │
│                 │     JWT (access token)    │   realm: company-realm│
└────────┬────────┘                           └──────────────────────┘
         │  (2) GET /api/... Bearer <JWT>              │
         ▼                                             │ JWKS (auto)
┌─────────────────┐  validates JWT signature ──────────┘
│  Spring Boot    │
│  :8080          │
│                 │
│  SecurityConfig │  URL-level roles
│  @PreAuthorize  │  Method-level roles
└─────────────────┘
```

---

## Realm Setup

| Resource  | Value                        |
|-----------|------------------------------|
| Realm     | `company-realm`              |
| Client    | `company-frontend` (public)  |
| Client    | `company-api` (bearer-only)  |

### Roles & Users

| Username | Password | Realm Role   | Permissions (client roles on company-api)                               |
|----------|----------|--------------|-------------------------------------------------------------------------|
| alice    | alice123 | app-admin    | report:read, report:approve, user:create, user:delete, admin:realm-view |
| bob      | bob123   | app-manager  | report:read, report:approve                                             |
| carol    | carol123 | app-user     | report:read  ← can read reports even though she is only app-user        |

### Groups

| Group    | Realm Role   | Client Permissions (company-api)                                        |
|----------|--------------|-------------------------------------------------------------------------|
| admins   | app-admin    | report:read, report:approve, user:create, user:delete, admin:realm-view |
| managers | app-manager  | report:read, report:approve                                             |
| users    | app-user     | report:read                                                             |

---

## Endpoints

### Role-based (`/api/user/**`, `/api/manager/**`, `/api/admin/**`)
Access determined by realm role — who you ARE.

| Method | Path                               | Required Role              |
|--------|------------------------------------|----------------------------|
| GET    | `/api/public/info`                 | None                       |
| GET    | `/api/public/health`               | None                       |
| GET    | `/api/user/profile`                | app-user / manager / admin |
| GET    | `/api/user/hello`                  | app-user / manager / admin |
| GET    | `/api/user/token-claims`           | app-manager / app-admin    |
| GET    | `/api/manager/reports`             | app-manager / app-admin    |
| POST   | `/api/manager/reports/{id}/approve`| app-manager / app-admin    |
| GET    | `/api/manager/team`                | app-manager / app-admin    |
| GET    | `/api/admin/users`                 | app-admin only             |
| POST   | `/api/admin/users`                 | app-admin only             |
| DELETE | `/api/admin/users/{username}`      | app-admin only             |
| GET    | `/api/admin/realm-config`          | app-admin only             |
| GET    | `/api/admin/me`                    | app-admin only             |

### Permission-based (`/api/resources/**`)
Access determined by specific permission — what you CAN DO, regardless of role.

| Method | Path                                  | Required Permission                       | alice | bob | carol |
|--------|---------------------------------------|-------------------------------------------|-------|-----|-------|
| GET    | `/api/resources/my-permissions`       | authenticated                             | ✓     | ✓   | ✓     |
| GET    | `/api/resources/reports`              | `report:read`                             | ✓     | ✓   | ✓     |
| POST   | `/api/resources/reports/{id}/approve` | `report:approve`                          | ✓     | ✓   | ✗ 403 |
| POST   | `/api/resources/reports/{id}/archive` | `report:read` AND `report:approve`        | ✓     | ✓   | ✗ 403 |
| POST   | `/api/resources/users`                | `user:create`                             | ✓     | ✗   | ✗ 403 |
| DELETE | `/api/resources/users/{username}`     | `user:delete`                             | ✓     | ✗   | ✗ 403 |

---

## Quick Start

### 1. Start Keycloak (Docker)

```bash
docker run --name keycloak \
  -p 8180:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:24.0.0 \
  start-dev
```

### 2. Import the Realm

**Option A — Admin UI:**
1. Open http://localhost:8180/admin → log in as `admin/admin`
2. Click "Create realm" → "Browse" → select `src/main/resources/keycloak/company-realm-export.json`
3. Click "Create"

**Option B — CLI (inside Docker):**
```bash
docker exec -it keycloak /opt/keycloak/bin/kc.sh import \
  --file /path/to/company-realm-export.json
```

### 3. Run the Spring Boot App

```bash
mvn spring-boot:run
```

Or build and run the JAR:
```bash
mvn clean package -DskipTests
java -jar target/keycloak-demo-1.0.0-SNAPSHOT.jar
```

---

## Testing with curl

### Get a token

**As Alice (admin):**
```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/company-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=company-frontend" \
  -d "username=alice" \
  -d "password=alice123" \
  -d "grant_type=password" \
  | jq -r '.access_token')

echo $TOKEN
```

**As Bob (manager):**
```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/company-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=company-frontend" \
  -d "username=bob" \
  -d "password=bob123" \
  -d "grant_type=password" \
  | jq -r '.access_token')
```

**As Carol (user):**
```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/company-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=company-frontend" \
  -d "username=carol" \
  -d "password=carol123" \
  -d "grant_type=password" \
  | jq -r '.access_token')
```

### Call the API

```bash
# Public — no token needed
curl http://localhost:8080/api/public/info | jq

# User profile — any authenticated user
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/user/profile | jq

# Manager reports — bob or alice only (carol gets 403)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/manager/reports | jq

# Approve a report (POST)
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/manager/reports/1/approve | jq

# Admin users list — alice only (bob and carol get 403)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/users | jq

# Create a user (admin only)
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"dave","email":"dave@company.com","role":"app-user"}' \
  http://localhost:8080/api/admin/users | jq

# Delete a user (admin only)
curl -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/admin/users/dave | jq

# Realm config summary (admin only)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/realm-config | jq

# Admin security context (admin only)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/me | jq
```

### Permission-based endpoints (get a token per user to see the difference)

```bash
# See your own resolved permissions and roles
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/resources/my-permissions | jq

# report:read — carol CAN call this even though she is only app-user
# Get carol's token first, then:
curl -H "Authorization: Bearer $CAROL_TOKEN" http://localhost:8080/api/resources/reports | jq
# → 200 OK  (carol has report:read)

# report:approve — carol CANNOT call this
curl -X POST \
  -H "Authorization: Bearer $CAROL_TOKEN" \
  http://localhost:8080/api/resources/reports/r-001/approve | jq
# → 403 Forbidden  (carol lacks report:approve)

# report:approve — bob CAN call this
curl -X POST \
  -H "Authorization: Bearer $BOB_TOKEN" \
  http://localhost:8080/api/resources/reports/r-001/approve | jq
# → 200 OK

# report:read AND report:approve — both required to archive
curl -X POST \
  -H "Authorization: Bearer $BOB_TOKEN" \
  http://localhost:8080/api/resources/reports/r-001/archive | jq
# → 200 OK (bob has both)

# user:create — alice only (bob gets 403 even though he is app-manager)
curl -X POST \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"dave"}' \
  http://localhost:8080/api/resources/users | jq
# → 403 Forbidden

curl -X POST \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"dave"}' \
  http://localhost:8080/api/resources/users | jq
# → 201 Created

# user:delete — alice only
curl -X DELETE \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  http://localhost:8080/api/resources/users/dave | jq
# → 200 OK
```

---

## How JWT Role Mapping Works

Keycloak issues JWTs with this structure:

Alice's JWT (app-admin with all permissions):

```json
{
  "sub": "...",
  "preferred_username": "alice",
  "realm_access": {
    "roles": ["app-admin", "offline_access", "default-roles-company-realm"]
  },
  "resource_access": {
    "company-api": {
      "roles": ["report:read", "report:approve", "user:create", "user:delete", "admin:realm-view"]
    }
  }
}
```

Carol's JWT (app-user with limited permissions):

```json
{
  "preferred_username": "carol",
  "realm_access": { "roles": ["app-user"] },
  "resource_access": {
    "company-api": { "roles": ["report:read"] }
  }
}
```

`KeycloakJwtConverter` maps these to Spring `GrantedAuthority`:

| JWT claim                                       | Spring Authority    | Check with                     |
|-------------------------------------------------|---------------------|--------------------------------|
| `realm_access.roles[app-admin]`                 | `ROLE_APP_ADMIN`    | `hasRole('APP_ADMIN')`         |
| `realm_access.roles[app-manager]`               | `ROLE_APP_MANAGER`  | `hasRole('APP_MANAGER')`       |
| `realm_access.roles[app-user]`                  | `ROLE_APP_USER`     | `hasRole('APP_USER')`          |
| `resource_access.company-api.roles[report:read]`    | `report:read`    | `hasAuthority('report:read')`  |
| `resource_access.company-api.roles[report:approve]` | `report:approve` | `hasAuthority('report:approve')`|
| `resource_access.company-api.roles[user:create]`    | `user:create`    | `hasAuthority('user:create')`  |
| `resource_access.company-api.roles[user:delete]`    | `user:delete`    | `hasAuthority('user:delete')`  |

---

## Project Structure

```
src/main/java/com/example/keycloakdemo/
├── KeycloakDemoApplication.java
├── config/
│   ├── SecurityConfig.java          # Spring Security filter chain + OAuth2 RS
│   └── KeycloakJwtConverter.java    # Maps realm_access.roles → ROLE_* authorities
├── controller/
│   ├── PublicController.java        # /api/public/**    — no auth
│   ├── UserController.java          # /api/user/**      — app-user+
│   ├── PermissionController.java    # /api/resources/** — permission-based (hasAuthority)
│   ├── ManagerController.java       # /api/manager/** — app-manager+
│   └── AdminController.java         # /api/admin/**  — app-admin only
├── dto/
│   ├── UserInfoDto.java
│   └── RealmConfigDto.java
└── service/
    └── UserContextService.java      # JWT claim helpers
```
