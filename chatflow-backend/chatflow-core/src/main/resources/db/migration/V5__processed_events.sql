-- ---------------------------------------------------------------------------
-- Consumer-side idempotency. Kafka is at-least-once, so a consumer may see the same outbox
-- event more than once. Each consumer records (consumer_group, event_id) after handling it and
-- skips anything already present — turning at-least-once delivery into effectively-once
-- processing. event_id is the stable outbox row id (OutboxEventMessage.id), constant across
-- redeliveries.
-- ---------------------------------------------------------------------------
create table processed_events (
    consumer_group varchar(128) not null,
    event_id       uuid         not null,
    processed_at   timestamp(6) with time zone not null,
    primary key (consumer_group, event_id)
);
