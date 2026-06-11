# chatflow-realtime

The **realtime gateway** — a dedicated, horizontally-scalable edge that terminates
WebSocket connections and holds the live sessions, so core no longer has to. **All business
logic stays in core**; this service is a thin, stateful socket edge:

- **Terminates `/ws`** and authenticates the handshake.
- **Holds the session registry** (which user has which open sockets, on this instance).
- **Delivers outbound frames** by subscribing to the Redis `chat:relay` channel.
- **Forwards inbound commands** to core's `/internal/realtime/*` API.
- **Reports connection lifecycle** (connect/disconnect) so core can drive presence.

Listens on **`:8083`**.

---

## When it's used

Core has a flag, `app.realtime.mode`:

- **`embedded`** (default) — core itself serves `/ws` (legacy / single-node fallback).
- **`external`** — **this service** serves `/ws`; core only publishes outbound frames to
  `chat:relay` and exposes `/internal/realtime/*` for inbound commands + lifecycle.

This module is the `external` half. Run it when you want to scale socket fan-out
independently of core's request workload.

---

## Where it sits

```
            client
              │  ws://…/ws?token=<JWT>
              ▼
   ┌─────────────────────────────┐
   │  chatflow-realtime :8083    │
   │  • JWT handshake auth       │      inbound: POST /internal/realtime/{connect,disconnect,inbound}
   │  • session registry         │ ───────────────────────────────────────────────▶ ┌──────────────┐
   │  • RealtimeWebSocketHandler │                                                   │ chatflow-core│
   │  • RelaySubscriber          │ ◀──────── chat:relay (Redis pub/sub) ──────────── │ :8080        │
   └─────────────────────────────┘            (outbound frames, verbatim)            └──────────────┘
```

**Outbound** (core → client): core publishes `{sourceInstanceId, targetUserId, payload}` to
`chat:relay`; `RelaySubscriber` delivers `payload` (already a complete frame) **verbatim** to
the target user's open sockets — no deserialization, no shared frame DTOs.

**Inbound** (client → core): `RealtimeWebSocketHandler` parses each frame generically as
`{type, requestId, payload}` and forwards it via `CoreCommandClient`. `PING` is answered
locally with `PONG`; everything else goes to core.

---

## Authentication

- **Handshake** — `JwtHandshakeInterceptor` reads the JWT from the `?token=` query param,
  validates it against the shared `app.jwt.secret`, and stashes the resolved `userId` on the
  WebSocket session. A missing/invalid token **rejects the handshake** (no socket opens).
- **To core** — `CoreCommandClient` calls `/internal/realtime/*` with the shared
  `X-Internal-Token` header (must match core's `INTERNAL_TOKEN`).

---

## Resilience & lifecycle semantics

`CoreCommandClient` is bounded by `RestClient` timeouts (2s connect / 3s read) and a circuit
breaker, with deliberately different failure handling per call kind:

| Call | On core 4xx (validation) | On core unavailable |
|------|--------------------------|---------------------|
| **inbound** (`/inbound`) | `CommandRejectedException` → `ERROR` frame to client (breaker ignores it — it's not a fault) | `CommandRejectedException("…temporarily unavailable")` → `ERROR` frame |
| **lifecycle** (`/connect`, `/disconnect`) | n/a | **best-effort** — logged, not propagated to the socket |

Session lifecycle:
- **First** session for a user → `core.connect(userId)` (core marks online + replays missed frames).
- **Last** session removed → `core.disconnect(userId)` (core marks offline + clears typing).

`RealtimeMetrics` exports Micrometer counters (`realtime.*`) for frames received, relay
messages delivered, etc.

---

## Configuration

All env-overridable (`application.yaml`):

| Concern | Key / Env var | Default |
|---------|---------------|---------|
| HTTP/WS port | `SERVER_PORT` | `8083` |
| Redis (relay) | `SPRING_DATA_REDIS_HOST` / `_PORT` | `localhost:6379` |
| core upstream | `CORE_BASE_URL` | `http://localhost:8080` |
| Internal token | `INTERNAL_TOKEN` | `dev-internal-token` (**must match core**) |
| JWT secret | `JWT_SECRET` | dev placeholder (**must match core**) |
| Instance id | `APP_INSTANCE_ID` | random UUID |
| WS idle timeout | `app.websocket.idle-timeout-seconds` | `90` |
| WS ping interval | `app.websocket.ping-interval-ms` | `30000` |
| Tracing | `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` |

Actuator exposes `health`, `info`, `metrics`, `prometheus`. The trace context is continued
across both the inbound REST hop and the relay hop.

---

## Running

> Set core to **`APP_REALTIME_MODE=external`** so it stops serving `/ws` and starts
> publishing frames to `chat:relay`. Otherwise both core and this service would bind `/ws`.

**Locally (from the reactor root):**

```bash
./mvnw -pl chatflow-realtime -am spring-boot:run
```

Requires Redis and a running core (`:8080`). Clients then connect to
`ws://localhost:8083/ws?token=<JWT>`.

**Build the jar:**

```bash
./mvnw -pl chatflow-realtime -am -DskipTests package
```

**Docker** (build context = reactor root, see `Dockerfile`):

```bash
docker compose --profile apps up --build realtime
```

---

## Tests

```bash
./mvnw -pl chatflow-realtime test
```

- `RealtimeWebSocketHandlerTest` — handshake auth, PING/PONG, inbound forwarding, malformed-frame and rejection → ERROR.
- `RelaySubscriberTest` — verbatim delivery to the target user's sockets, ignores malformed envelopes.
- `ChatflowRealtimeApplicationTests` — context loads.

---

## Stack

- Java 21, Spring Boot 4.0 (websocket + actuator), Spring Data Redis (pub/sub)
- Spring Cloud 2025.1.1 (circuit breaker — Resilience4j), `RestClient`
- jjwt 0.12.6 (handshake JWT validation)
- Micrometer + Prometheus, OpenTelemetry (OTLP)

Relates to the living migration plan in [`../docs/microservices-migration.md`](../docs/microservices-migration.md)
(Phase 3).
