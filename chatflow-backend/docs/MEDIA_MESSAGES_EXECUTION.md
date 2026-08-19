# Media Messages — Execution Plan

Backend feature set for sending images, video, audio, and files through ChatFlow:
upload → validate → store → persist metadata → push over WebSocket → process →
serve via signed URLs → clean up on delete.

Package root: `com.chatflow.media`

## Legend

- ✅ **Done** — implemented and compiling
- ⚠ **Partial** — some files exist; listed gaps must be closed before it works
- ⬜ **Todo** — not started

## Status at a glance

| Phase | Feature | Status | Blocking gaps |
|------|---------|--------|---------------|
| P1 | Core upload + metadata | ✅ Done | — |
| P2 | Validation | ✅ Done | — |
| P3 | Local storage abstraction | ✅ Done | — |
| P4 | WebSocket delivery | ✅ Done | — |
| P5 | MinIO / S3 storage | ✅ Done | tested (mock round-trip + bean wiring); live MinIO round-trip pending env |
| P6 | Thumbnail generation | ✅ Done | tested (real image → thumbnail, fallbacks) |
| P7 | Media cleanup on delete | ✅ Done | tested (mark→purge→retry, auth, failure handling) |
| P8 | Signed URLs | ✅ Done | tested (presigned URL + expiry, participant gate) |

**All 8 phases implemented.** Unit-test coverage: 26 tests across the media
module (thumbnailing, S3 storage, bean wiring, access guard, purger, cleanup,
signed URLs). Outstanding before production: live MinIO round-trip (P5, needs
Docker), local-profile JWT signing (P8), and end-to-end integration against a
running app + DB.

Dependencies present in `pom.xml`: `software.amazon.awssdk:s3`, `net.coobird:thumbnailator`.

---

## Security rules (non-negotiable)

These apply across every phase. Treat them as acceptance criteria, not suggestions.

1. **Never use `MultipartFile.getOriginalFilename()` as a storage key.** It can carry
   path traversal (`../../etc/passwd`). Generate a UUID filename; keep the original
   only as metadata.
2. **The `Content-Type` header from the browser is untrusted.** Verify the real type
   from the file's magic bytes (Apache Tika / `Files.probeContentType()`). A `.jpg`
   wrapping an `.exe` will declare `image/jpeg`.
3. **Store files outside the web root.** Never under `src/main/resources/static` —
   files served from there bypass auth entirely.
4. **Storage is not transactional.** Never delete a storage object inside the DB
   transaction. Commit the DB change first, then delete storage; retry async on failure.
5. **No public buckets.** Objects are private; access is granted only through
   time-limited signed URLs, and only after a participant auth check.

---

## P1 — Core upload + metadata ✅

`MessageType` enum (IMAGE, VIDEO, AUDIO, FILE), `MediaMessage` entity with all metadata,
`POST /api/messages/media` multipart endpoint, UUID filename generation, metadata
persisted to PostgreSQL.

**Files**
- `media/entity/MediaMessage.java`
- `media/entity/MessageType.java`
- `media/entity/MediaStatus.java`
- `media/repository/MediaMessageRepository.java`
- `media/dto/MediaUploadRequest.java`
- `media/dto/MediaMessageResponse.java`
- `media/controller/MediaController.java`
- `media/service/MediaMessageService.java`

---

## P2 — Validation ✅ — needs P1

Centralized `MediaValidator`: max size per type, allowed-MIME whitelist, reject empty
files, block dangerous extensions (`.exe`, `.sh`, `.php`). `@ControllerAdvice` returns
clean 4xx responses. Rules are config-driven in `application.yml` so limits change
without redeploy.

**Files**
- `media/validation/MediaValidator.java`
- `media/validation/MediaValidationConfig.java`
- `media/exception/MediaValidationException.java`

> Verify the type from magic bytes, not the declared `Content-Type` (rule 2).

---

## P3 — Local storage abstraction ✅ — needs P1, P2

`MediaStorageService` interface with `store()`, `storeBytes()`, `delete()`, `getUrl()`.
`LocalMediaStorageService` writes to a configurable directory with nested folders:
`uploads/{type}/{year}/{month}/{uuid}.ext`. The interface is the swap point for S3 in
P5 — no callers change.

**Files**
- `media/storage/MediaStorageService.java`
- `media/storage/LocalMediaStorageService.java`
- `media/storage/StoredMedia.java`
- `media/storage/StorageException.java`

> Store outside the web root (rule 3).

---

## P4 — WebSocket delivery ✅ — needs P1–P3

After upload + persist, push `MEDIA_MESSAGE` to conversation/group participants via
`WebSocketGateway`. Payload: `mediaUrl`, `mimeType`, `fileSize`, `messageType`,
`senderId`, timestamps. Reuses `OutboundMessage.Type` (the `MEDIA_MESSAGE` value is
added). The existing cross-server relay handles delivery to other instances.

**Files**
- `infra/websocket/OutboundMessage.java` — `MEDIA_MESSAGE` type added
- `media/service/MediaMessageService.java` — `deliverToParticipants(...)`

---

> **Live MinIO round-trip (P5)** — Docker wasn't available in the dev env where
> this was built, so the live network round-trip is not yet run. To do it:
> ```bash
> docker run -d --name minio -p 9000:9000 -p 9001:9001 \
>   -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
>   minio/minio server /data --console-address ":9001"
> # create the bucket (mc) or via the console at :9001, then:
> SPRING_PROFILES_ACTIVE=s3 ./mvnw spring-boot:run
> ```

## P5 — MinIO / S3 storage ✅ — needs P3

`S3MediaStorageService implements MediaStorageService` using AWS SDK v2. MinIO is
S3-compatible — same code, different endpoint URL. `@Profile("s3")` activates it;
`@Profile("local")` keeps `LocalMediaStorageService`. Add presigned-URL generation to
the interface in preparation for P8.

**Files to create**
- `media/storage/S3MediaStorageService.java`
- `media/storage/S3Config.java`

**Steps**
1. Add `@Profile("local")` to `LocalMediaStorageService`.
2. Add `S3Config`: `S3Client` + `S3Presigner` beans wired from config
   (`endpoint`, `region`, `bucket`, credentials). Endpoint override + path-style access
   for MinIO.
3. Implement `S3MediaStorageService` (`store`, `storeBytes`, `delete`, `getUrl`) under
   `@Profile("s3")`.
4. Extend `MediaStorageService` with `String presignedUrl(String key, Duration ttl)`;
   implement in both profiles (P8 consumes it).
5. Config keys under `chatflow.media.storage.*` in `application.yml`.

**Acceptance:** switching the active profile changes storage backend with zero caller
changes; bucket is private.

---

## P6 — Thumbnail generation ⚠ — needs P3 / P5

Async via `ApplicationEventPublisher`. Thumbnails stored with a separate URL on
`MediaMessage`. Images → Thumbnailator; videos → FFmpeg via `ProcessBuilder`. Fallback:
thumbnail stays null, client shows a placeholder. **Never blocks the upload response.**

**Already present**
- `media/processing/ThumbnailEventListener.java` — `@Async` listener that persists the
  thumbnail URL and pushes `MEDIA_THUMBNAIL_READY`.
- `OutboundMessage.Type.MEDIA_THUMBNAIL_READY`.

**Missing — close these to compile/run**
- `media/processing/ThumbnailGeneratedEvent.java` — referenced by the listener but does
  not exist (`mediaMessageId`, `thumbnailUrl`).
- `media/processing/ThumbnailService.java` — actually generates the thumbnail (image via
  Thumbnailator, video via FFmpeg), stores it via `MediaStorageService.storeBytes(...)`,
  and publishes `ThumbnailGeneratedEvent`.
- An `@Async` executor bean named `mediaProcessingExecutor` (the listener references it).
- Hook upload → trigger thumbnail generation after the DB commit (e.g. publish a
  "media uploaded" event or call `ThumbnailService` post-commit).

**Acceptance:** uploading an image returns immediately with `thumbnailUrl: null`, then a
`MEDIA_THUMBNAIL_READY` frame arrives once generation completes; failures log and leave
the URL null.

---

## P7 — Media cleanup on delete ⬜ — needs P3 / P5, P6

`DELETE /api/messages/media/{id}`. Deletes the DB record, the storage object, and the
thumbnail. Outbox-style: mark for deletion in DB first (within the transaction), then
delete from storage; if storage delete fails, a cleanup job retries. Avoids orphaned
files.

**Files**
- `media/service/MediaCleanupService.java` — new
- `media/controller/MediaController.java` — add `DELETE /{id}`

**Steps**
1. Authorize: caller must be the sender (or have delete rights).
2. In a transaction, mark the record deleted / enqueue for storage cleanup
   (`MediaStatus`).
3. After commit, delete the storage object + thumbnail.
4. On storage failure, log and leave the row flagged; a scheduled job
   (`@Scheduled`) retries flagged rows.

> Never delete storage atomically with the DB transaction (rule 4).

---

## P8 — Signed URLs ⬜ — needs P5

`GET /api/messages/media/{id}/url` returns a presigned URL valid for N minutes. Auth:
caller must be a participant in the conversation/group before a URL is issued. Private
S3 bucket — objects are never directly accessible. The `local` profile returns a
JWT-protected signed URL instead.

**Files**
- `media/service/MediaAccessService.java` — new
- `media/controller/MediaController.java` — add `GET /{id}/url`

**Steps**
1. Load the message; run the same participant check used by `getById`.
2. `s3` profile → `S3Presigner` (from P5) with a configurable TTL.
3. `local` profile → short-lived signed JWT URL backed by an authenticated endpoint.
4. Return `{ url, expiresAt }`.

> Reuse the read-access check; never issue a URL to a non-participant (rule 5).

---

## Suggested execution order

1. **Fix P6 compile gap** — add `ThumbnailGeneratedEvent`, `ThumbnailService`, and the
   `mediaProcessingExecutor` bean. (The tree currently references types that don't exist.)
2. **P5** — S3/MinIO + presigned-URL method on the interface.
3. **P8** — signed URLs (depends on P5's presigner).
4. **P7** — cleanup + scheduled retry.

## Verification checklist

- [ ] `mvn -q -pl chatflow-backend compile` succeeds (closes P6 gap).
- [ ] Upload of each `MessageType` validates, stores outside web root, persists metadata.
- [ ] Path-traversal filename and `.exe`-as-`.jpg` are both rejected.
- [ ] `MEDIA_MESSAGE` reaches all participants, including across instances.
- [ ] Thumbnail arrives asynchronously; upload response is never blocked.
- [ ] `local` ↔ `s3` profile swap requires no caller changes.
- [ ] Signed URL issued only to participants; expires after the configured TTL.
- [ ] Delete removes DB row + object + thumbnail; storage failure retries, no orphans.
