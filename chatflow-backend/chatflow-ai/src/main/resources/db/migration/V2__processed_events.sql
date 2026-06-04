-- ---------------------------------------------------------------------------
-- Consumer-side idempotency for ai-service (see core's V5 for the rationale). Keyed on
-- (consumer_group, event_id) = the stable outbox row id. The embedding upsert is already
-- idempotent on message_id, so this is belt-and-suspenders, but it keeps the dedup pattern
-- uniform across services.
-- ---------------------------------------------------------------------------
create table processed_events (
    consumer_group varchar(128) not null,
    event_id       uuid         not null,
    processed_at   timestamp(6) with time zone not null,
    primary key (consumer_group, event_id)
);
