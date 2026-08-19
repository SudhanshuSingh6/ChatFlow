# Message Search

Case-insensitive substring search over message text, scoped strictly to what the
caller is authorized to read. Results are newest-first with an opaque keyset cursor.

Package: `com.chatflow.message` (service/controller/dto) + `SearchCursor`.

## Endpoints

All require auth (JWT); `q` is the query, `limit` defaults to 20 (max 50), `cursor`
is the opaque `nextCursor` from the previous page.

| Method | Path | Scope |
|--------|------|-------|
| GET | `/api/search/messages?q=&cursor=&limit=` | Global — all the caller's direct + group messages |
| GET | `/api/conversations/{conversationId}/messages/search?q=&cursor=&limit=` | One conversation |
| GET | `/api/groups/{groupId}/messages/search?q=&cursor=&limit=` | One group |

All return `SearchPageResponse`:

```json
{
  "results": [
    {
      "type": "DIRECT",            // or "GROUP"
      "messageId": "…",
      "conversationId": "…",        // null for GROUP
      "groupId": null,              // set for GROUP
      "senderId": "…",
      "receiverId": "…",            // null for GROUP
      "content": "full message text",
      "sequenceNumber": 42,
      "createdAt": "2026-05-29T18:30:15"
    }
  ],
  "nextCursor": "eyJ0…"            // null on the last page
}
```

## Design

- **Authorization is the query predicate** — no post-filtering, no leakage:
  - Direct: `WHERE (senderId = :me OR receiverId = :me)` (a 1:1 message always has
    the caller as one party).
  - Group: `WHERE groupId IN (SELECT groupId FROM GroupMember WHERE userId = :me)`.
  - Scoped endpoints additionally run the participant/membership check up front.
- **Matching**: `LOWER(content) LIKE :term ESCAPE '\'`. The query is trimmed,
  lowercased, and `\ % _` are escaped so user input can't widen the match. Min
  length 2 (→ 400).
- **Paging**: keyset cursor on `(createdAt, id)` — direct and group hits share no
  sequence-number space, so recency is the common ordering. The cursor is
  Base64(`createdAt|id`). Global search over-fetches one row per source and merges,
  so `hasMore`/`nextCursor` stay correct even when one source is exhausted.
- Errors: `IllegalArgumentException` → 400 (bad query/cursor, conversation not
  found), `SecurityException` → 403 (not a participant/member). Both already mapped
  in `RestExceptionHandler`.

## Verified

- `mvn compile` clean; 12 unit tests pass (`MessageSearchServiceTest`,
  `SearchCursorTest`): validation, wildcard escaping, participant/membership denial,
  result mapping, global merge ordering, pagination + cursor round-trip, limit clamp.
- **Not yet exercised against a live DB** — the JPQL (incl. the cross-entity
  membership subquery and `ESCAPE`) is validated by Hibernate at app startup, which
  needs Postgres credentials not available in this env.

## Follow-up — full-text relevance (deferred)

Substring `LIKE '%term%'` can't use a btree index. For relevance ranking and scale:
add a Postgres `tsvector` generated column + GIN index on `messages` and
`group_messages`, switch to `websearch_to_tsquery`, order by `ts_rank`, and use
`ts_headline` for snippets. This needs a real SQL migration (the project uses
`ddl-auto=update`, which can't create GIN/tsvector); `pg_trgm` GIN is the
alternative to accelerate `LIKE`.
