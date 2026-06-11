# chatflow-gateway

The public entry point for ChatFlow's **HTTP** traffic. Every external REST/AI call
(browser, mobile) talks to the gateway; core and ai sit behind it. The gateway does two jobs:

1. **Route** incoming requests to the right service by path prefix.
2. **Authenticate at the edge** — reject anything without a valid JWT before it ever
   reaches a downstream service.

It is stateless, holds no database, and is built on **Spring Cloud Gateway (servlet/webmvc
variant)** to stay consistent with the rest of the servlet stack.

> **WebSockets do not go through the gateway.** Realtime clients connect **directly** to
> `chatflow-realtime` at `ws://…:8083/ws?token=<JWT>`, which authenticates the JWT at the
> handshake. The servlet gateway variant does not proxy WS upgrades, and terminating the
> socket at the dedicated realtime edge (rather than tunnelling it through the REST gateway)
> is a deliberate choice — it's a common pattern for separately-scaled realtime services. So
> the gateway is the single entry point for **HTTP**, not for the WebSocket.

---

## Where it sits

```
                       ┌──────────────────────────┐
   client  ──HTTP──▶   │   chatflow-gateway :8088 │
                       │   • route by path        │
                       │   • validate JWT (edge)  │
                       └────────────┬─────────────┘
                       /api/**       │       /ai/**
                  ┌─────────────────┘└─────────────────┐
                  ▼                                     ▼
        chatflow-core :8080                    chatflow-ai :8081

   client  ──WS (direct, bypasses gateway)──▶  chatflow-realtime :8083  /ws?token=<JWT>
```

In production, core (`:8080`) and ai (`:8081`) would be internal-only and the gateway
would own the public HTTP port. The realtime edge (`:8083`) is exposed separately for the
WebSocket.

---

## Routing

Configured declaratively in `src/main/resources/application.yaml`:

| Path predicate | Routes to                     | Default URI               |
|----------------|-------------------------------|---------------------------|
| `/ai/**`       | ai-service                    | `http://localhost:8081`   |
| `/api/**`      | core-service                  | `http://localhost:8080`   |

Targets are overridable via `AI_URI` / `CORE_URI` environment variables (used in
Docker Compose and Kubernetes, where they point at service DNS names).

---

## Edge authentication

`EdgeAuthFilter` (a `OncePerRequestFilter`) runs on every request and:

- Extracts the `Bearer` token from the `Authorization` header.
- Validates it via `JwtValidator`, which verifies the **HMAC signature** against the
  shared `app.jwt.secret` (it only *validates* — it never mints tokens).
- Returns **`401`** with `{"error":"Missing or invalid token"}` if the token is missing
  or invalid; otherwise the request continues to the route.

**Bypassed** (`shouldNotFilter`) for:

- `/api/auth/**` — login/register must be reachable without a token.
- `/actuator/**` — health/info probes.
- `OPTIONS` requests — CORS preflight.

> **Defence in depth:** the gateway forwards the original token downstream, and core/ai
> **re-verify** the JWT themselves. Validating at the edge fails bad requests fast; it
> does not replace per-service auth. (Terminating auth at the edge and injecting
> `X-User-Id` is a parked follow-up.)

---

## Configuration

| Key / Env var                     | Default                          | Purpose                                  |
|-----------------------------------|----------------------------------|------------------------------------------|
| `SERVER_PORT`                     | `8088`                           | Public listen port.                      |
| `CORE_URI`                        | `http://localhost:8080`          | core-service upstream.                   |
| `AI_URI`                          | `http://localhost:8081`          | ai-service upstream.                     |
| `JWT_SECRET` (`app.jwt.secret`)   | dev placeholder (≥ 32 chars)     | **Must match core/ai.** Used to validate token signatures. |

Actuator exposes `health`, `info`, and `gateway` (the latter lists active routes) at
`/actuator/**`.

---

## Running

**Locally (from the reactor root):**

```bash
./mvnw -pl chatflow-gateway -am spring-boot:run
```

This assumes core (`:8080`) and ai (`:8081`) are already running. The gateway is then
reachable at `http://localhost:8088`.

**Build the jar:**

```bash
./mvnw -pl chatflow-gateway -am -DskipTests package
```

**Docker** (build context is the reactor root so the parent pom + contracts are
available — see `Dockerfile`):

```bash
docker compose --profile apps up --build gateway
```

In Compose/Kubernetes, `CORE_URI` / `AI_URI` / `JWT_SECRET` are injected from the
environment (`.env`) / Secret.

---

## Tests

```bash
./mvnw -pl chatflow-gateway test
```

- `JwtValidatorTest` — signature validation accepts valid tokens, rejects tampered/expired/garbage.
- `EdgeAuthFilterTest` — 401 on missing/invalid token, pass-through on valid, bypass for the exempt paths.
- `ChatflowGatewayApplicationTests` — context loads.

---

## Stack

- Java 21, Spring Boot 4.0, Spring Cloud 2025.1.1 (`spring-cloud-starter-gateway-server-webmvc`)
- `io.jsonwebtoken` (jjwt 0.12.6) for edge JWT validation
- Spring Boot Actuator
