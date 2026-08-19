# Plan: Unify Chat Schema (one conversation model + outbox)

**Status:** DONE (2026-06-01) — all 6 phases implemented. The old `message.*` and
`group.*` packages are deleted; everything runs on `conversation.*`. Notifications +
transactional outbox added; media re-linked to `message_id`. `V1__init.sql` is the
unified schema and Hibernate runs with `ddl-auto=validate`.
**One-time op:** the dev DB must be dropped once before first boot (clean-wipe, no
backfill), since `V1` is a fresh full schema and `baseline-on-migrate` is now off.
**Why:** the 1:1 and group stacks are near-duplicate parallel implementations
(`Message`/`GroupMessage`, `Conversation`-participants/`Group`+`GroupMember`,
`ChatService`/`GroupChatService`, `DeliveryService`/`GroupDeliveryService`,
plus two replay paths and two sets of WS message types). Collapsing both into a
single conversation model removes that duplication, and we add `notifications` and a
transactional `outbox_events` table.

## Decisions (locked)
- **Data:** dev-only — **clean wipe, no backfill**. New schema is created fresh.
- **Delivery:** **hybrid** — keep direct WebSocket push for low-latency live delivery
  **and** write `outbox_events` for durability, notifications, and cross-server relay.

---

## 1. Table mapping (8 → 8)

| Current | Target | Change |
|---|---|---|
| `users` | `users` | unchanged |
| `friendships` | `friendships` | unchanged |
| `conversations` (participantOne/Two) | `conversations` (+ `type`) | generalized to DIRECT + GROUP |
| `groups` | — | merged into `conversations` (type=GROUP) |
| `group_members` | `conversation_participants` | merged; also replaces participantOne/Two |
| `messages` (+status, receiverId) | `messages` (unified) | merged with group_messages; status → watermarks |
| `group_messages` | — | merged into `messages` |
| `media_messages` (conv OR group id) | `media_messages` (→ `message_id`) | re-linked to a message row |
| — | `notifications` | new (see notification-feature.md) |
| — | `outbox_events` | new (transactional outbox) |

Drop: `groups`, `group_messages`. Repurpose: `group_members` → `conversation_participants`.
Add: `notifications`, `outbox_events`.

---

## 2. Target schema (key columns)

**conversations** — DIRECT or GROUP
- `id`, `type` (`DIRECT`|`GROUP`)
- `name` (null for DIRECT), `created_by` (null for DIRECT)
- `dm_key` (unique, null for groups) = `min(userA,userB):max(userA,userB)` — prevents
  duplicate DMs (replaces today's canonical participantOne/Two ordering)
- `last_message_preview`, `last_message_at`, `last_message_seq` (denormalized for list)
- `created_at`, `updated_at`

**conversation_participants** — membership for both kinds
- `id` (or composite PK `(conversation_id, user_id)`)
- `conversation_id`, `user_id`
- `role` (`OWNER`|`ADMIN`|`MEMBER`; DIRECT → both MEMBER)
- `last_read_seq`, `last_delivered_seq` (read/delivery watermarks)
- `joined_at`, optional `muted`, optional `left_at`
- unique `(conversation_id, user_id)`

**messages** — unified
- `id`, `conversation_id`, `sender_id`
- `client_message_id` (unique — idempotency)
- `content` (nullable for media-only), `type` (`TEXT`|`MEDIA`|`SYSTEM`)
- `sequence_number`, unique `(conversation_id, sequence_number)`
- `created_at`, `edited_at`, `deleted_at` (soft delete)
- **No per-message `status`/`receiver_id`** — see §3.

**media_messages** — `message_id` FK + `message_type`, `storage_key`, `thumbnail_url`,
`status`, `deleted`. (Drops conv/group linkage; a media message is a `messages` row of
`type=MEDIA` plus this detail row.)

**notifications** — recipient_id, actor_id, type, reference_type/id, preview,
event_count, read, created_at, read_at (per `notification-feature.md`).

**outbox_events** — `id`, `aggregate_type`, `aggregate_id`, `event_type`,
`payload` (jsonb), `created_at`, `published_at` (null=pending), `status`
(`PENDING`|`PUBLISHED`).

---

## 3. Status → per-participant watermarks (the key normalization)

Today each `messages` row carries `status` (SENT/DELIVERED/SEEN) + `receiver_id`,
which only models 1:1. Groups already track per-member sequence watermarks. The
unified model uses **watermarks for everyone**:

- "Has user X read message N?" → `participant(X).last_read_seq >= N`.
- 1:1 ticks derive from the *other* participant's watermarks:
  - ✓ sent (message persisted)
  - ✓✓ delivered → other participant's `last_delivered_seq >= seq`
  - ✓✓ blue (seen) → other participant's `last_read_seq >= seq`
- Group read/delivery receipts use the exact same logic — one code path.

This deletes the per-message status-update machinery duplication entirely.

---

## 4. Outbox (hybrid model)

Each state-changing `@Transactional` method, in addition to its **direct** live push
(kept for latency), writes one `outbox_events` row in the same transaction.

A poller (`@Scheduled`, `SELECT … FOR UPDATE SKIP LOCKED`) reads PENDING events and a
single consumer dispatches them to:
- **notification creation** (e.g. `message.created`, `friend.requested`),
- **cross-server relay** (publish to Redis for users on other instances),
- any other durable side effect,

then sets `published_at`. Division of labour:

| Path | Who | Guarantee |
|---|---|---|
| Direct push (`AfterCommit` → `WebSocketGateway`) | locally-connected recipients | best-effort, low latency |
| Outbox poller | notifications, cross-server fan-out, crash recovery | at-least-once, durable |

This keeps today's snappy live delivery while making notifications and cross-instance
delivery crash-safe, and replaces ~15 scattered `AfterCommit` fan-out sites with one
consumer. Clients already de-dupe by `sequence_number` / `client_message_id`, so the
hybrid can't double-show a message.

---

## 5. Code refactor (merge → delete duplicates)

- `Conversation` gains `type`/`name`/`dmKey`; **delete `Group`**.
- New `ConversationParticipant`; **delete `GroupMember`**; drop participantOne/Two.
- `Message` unified; **delete `GroupMessage`**.
- `ChatService` handles both (branch on `type` only for naming/role rules);
  **delete `GroupChatService`**.
- `DeliveryService` watermark-based for both; **delete `GroupDeliveryService`**.
- One `ReplayService`: replay = messages with `seq > participant.last_delivered_seq`.
- `MediaMessageService` attaches media to a `messages` row.
- **WS protocol:** collapse `MESSAGE`/`GROUP_MESSAGE`, the `*_ACK`s, and receipts into
  single types carrying `conversationId` (a group is just a conversation). Large
  `InboundMessage.Type` / `OutboundMessage.Type` simplification.
- **REST:** unify under `/api/conversations` with `type`; group management becomes
  sub-resources: `POST /api/conversations`, `/{id}/participants`,
  `/{id}/participants/{userId}/role`, `/{id}/messages`. Deprecate `/api/groups/*`.
  *Timing is good: the frontend is still on mock data (REST not wired), so no client
  breakage.*

---

## 6. Migration strategy (dev-only wipe)

Schema is now load-bearing → **introduce Flyway**:
1. Add `flyway-core` + `flyway-database-postgresql`; set `spring.jpa.hibernate.ddl-auto: validate`.
2. **Drop the dev database once.**
3. `V1__init.sql` = the **final unified schema** (no backfill needed since we wiped).
4. Hibernate `validate` confirms entities match the migration on boot.

(If you'd rather defer Flyway: set `ddl-auto: create` for one boot to rebuild from the
new entities, then back to `update`. Flyway is the recommended path now.)

---

## 7. Phased rollout

1. **Schema** — new entities (`Conversation`+type, `ConversationParticipant`,
   unified `Message`), Flyway `V1__init.sql`, wipe DB, `ddl-auto: validate`.
2. **Repos/services** — merge Chat/Delivery/Replay onto unified entities; delete
   `Group*` duplicates; watermark receipts.
3. **WS + REST** — collapse message types; unify controllers under `/api/conversations`.
4. **Outbox** — table, transactional writer, poller, consumer; wire notifications +
   cross-server relay; keep direct push.
5. **Notifications** — build on unified `message.created` outbox events
   (per `notification-feature.md`).
6. **Media** — re-link to `message_id`; media message = `messages` row type=MEDIA.

Each phase ends green on the test suite (group tests get rewritten against the unified
model).

---

## 8. Risks / watch-outs
- **Watermark receipts** — the trickiest correctness area; needs solid tests for 1:1
  tick parity (delivered/seen derived from the counterpart participant).
- **DM uniqueness** under concurrency — rely on `dm_key` unique constraint + catch
  `DataIntegrityViolationException` (same pattern `friendships` already uses).
- **Outbox ordering** — preserve per-conversation order in the poller, or rely on
  client `sequence_number` ordering.
- **System messages** (`type=SYSTEM`) are new — "Alice added Bob", "Carol left".
- **Sequence allocation** — unified per-conversation `next_sequence_number` under
  contention (keep the existing locking approach).
