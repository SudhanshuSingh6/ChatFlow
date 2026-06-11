# chatflow-media

A **stateless media-processing worker**. Its one job is to keep the CPU-heavy thumbnailing
(Thumbnailator for images, an FFmpeg frame-grab for video) **off the chat hot path**. It
holds **no database** and exposes no business API — all work is event-driven.

Listens on **`:8082`** (HTTP only for health/metrics; the actual work arrives over Kafka).

---

## Why processing only

Media *upload* could not be extracted: in core it is transactionally a message creation
(it locks the conversation, allocates the next sequence number, and inserts the `messages`
row). So **core keeps upload + media metadata**, and this service takes only the part that
benefits from isolation — the slow, bursty thumbnail generation.

```
  POST /api/messages/media          MediaProcessingRequested            MediaThumbnailReady
        │  (core: insert row,        (outbox → Kafka:                    (Kafka:
        │   write original to store) │ chatflow.outbox.events)           │ chatflow.media.thumbnail-ready)
        ▼                            ▼                                   ▼
  ┌────────────┐   emits event   ┌──────────────────┐   emits event   ┌────────────┐
  │ chatflow-  │ ──────────────▶ │ chatflow-media    │ ──────────────▶ │ chatflow-  │
  │ core :8080 │                 │ :8082 (this)      │                 │ core :8080 │
  └────────────┘                 │ • read original   │                 │ • set      │
        ▲                        │   from store      │                 │   thumb_url│
        │ shared object store    │ • thumbnail       │                 │ • push to  │
        └────────────────────────┤ • write back      │                 │   clients  │
          (MinIO / S3, or a      └──────────────────┘                  └────────────┘
           shared upload dir)
```

The original and the thumbnail both live in the **shared object store** — media reads the
original and writes the thumbnail back; it never streams bytes over the wire to core. Core
is told only the resulting thumbnail URL.

---

## How it works

1. **`MediaProcessingConsumer`** (`@KafkaListener`) subscribes to the shared outbox topic
   `chatflow.outbox.events` under its **own consumer group** (`chatflow-media`) so it
   receives events independently of core. It filters to `MediaProcessingRequested.TYPE` and
   ignores everything else.
2. **`ThumbnailService`** reads the original from the store by `storageKey`, then:
   - **IMAGE** → resize with Thumbnailator (max `320px`), output JPEG.
   - **VIDEO** → grab a frame ~1s in via the `ffmpeg` binary, then resize that.
   - **AUDIO / FILE** → no thumbnail.
   It is **fail-soft**: any failure (bad input, ffmpeg timeout, etc.) returns *empty* and is
   logged at WARN — the client just shows a placeholder. It never throws.
3. On success it publishes **`MediaThumbnailReady`** (`mediaMessageId` + `thumbnailUrl`) to
   `chatflow.media.thumbnail-ready`, which core's `MediaThumbnailReadyListener` consumes to
   persist the URL and push `MEDIA_THUMBNAIL_READY` to participants.

**Idempotent by construction:** delivery is at-least-once, but the thumbnail is written to a
deterministic key (`MediaKeys.thumbnailKey`), so a redelivery just regenerates and overwrites.

Contracts (`MediaProcessingRequested`, `MediaThumbnailReady`) live in **chatflow-contracts**,
shared with core.

---

## Storage

Pluggable via Spring profile, mirroring core's storage abstraction:

| Profile        | Implementation            | Notes |
|----------------|---------------------------|-------|
| default (local)| `LocalMediaStorageService`| `MEDIA_UPLOAD_DIR` **must be the same directory core writes originals to.** |
| `s3`           | `S3MediaStorageService`   | Shared MinIO/S3 bucket with core (`app.s3.*`). |

---

## Configuration

All env-overridable (`application.yaml`):

| Concern | Key / Env var | Default |
|---------|---------------|---------|
| HTTP port | `SERVER_PORT` | `8082` |
| Kafka | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| Inbound topic | `APP_OUTBOX_TOPIC` | `chatflow.outbox.events` |
| Consumer group | `APP_MEDIA_CONSUMER_GROUP` | `chatflow-media` |
| Outbound topic | `APP_MEDIA_THUMBNAIL_READY_TOPIC` | `chatflow.media.thumbnail-ready` |
| Local store dir | `MEDIA_UPLOAD_DIR` | `./uploads` (must match core) |
| Thumbnail max size | `app.media.thumbnail.max-size` | `320` px |
| FFmpeg binary | `FFMPEG_PATH` | `ffmpeg` (on PATH) |
| FFmpeg timeout | `app.media.thumbnail.ffmpeg-timeout-seconds` | `20` s |
| Object store (`s3` profile) | `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION` | MinIO @ `localhost:9000` |
| Tracing | `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` |

Actuator exposes `health`, `info`, `metrics`, `prometheus`. The trace context is propagated
across the Kafka hop (`observation-enabled`), so a thumbnail span links back to the originating
upload trace.

---

## Running

> **Requires the `ffmpeg` binary** on PATH for video thumbnails (the Docker image installs it).
> Image thumbnails work without it.

**Locally (from the reactor root):**

```bash
./mvnw -pl chatflow-media -am spring-boot:run
```

Requires Kafka, and the shared store (a local `uploads/` dir, or MinIO under the `s3`
profile) reachable. Core must be running in **`kafka` outbox mode** for events to flow.

**Build the jar:**

```bash
./mvnw -pl chatflow-media -am -DskipTests package
```

**Docker** (build context = reactor root; image installs `ffmpeg`):

```bash
docker compose --profile apps up --build media
```

---

## Tests

```bash
./mvnw -pl chatflow-media test
```

- `MediaProcessingConsumerTest` — filters non-media events, generates + publishes on a hit, stays silent when no thumbnail is produced.
- `MediaKeysTest` — deterministic thumbnail key derivation.
- `ChatflowMediaApplicationTests` — context loads.

---

## Stack

- Java 21, Spring Boot 4.0 (web + actuator — work itself is event-driven), Spring Kafka
- Thumbnailator (images) + FFmpeg binary (video frames)
- AWS SDK v2 (S3/MinIO)
- Micrometer + Prometheus, OpenTelemetry (OTLP)

Relates to the living migration plan in [`../docs/microservices-migration.md`](../docs/microservices-migration.md)
(Phase 2).
