-- ChatFlow baseline schema (V1) — unified conversation model.
--
-- One conversation model serves both 1:1 (DIRECT) and group (GROUP) chats; the old
-- separate groups/group_messages tables and the participantOne/Two columns are gone.
-- Delivery/read state lives on conversation_participants as sequence watermarks.
-- Adds notifications and a transactional outbox (outbox_events).
--
-- This mirrors the schema Hibernate generates from the JPA entities, so with
-- spring.jpa.hibernate.ddl-auto=validate the app boots clean against it.
-- Conventions: snake_case columns (CamelCaseToUnderscoresNamingStrategy),
-- Instant -> timestamp with time zone, LocalDateTime -> timestamp, no FKs (entities
-- hold plain UUIDs). Enum columns are @Enumerated(STRING) -> varchar.

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
create table users (
    id         uuid         not null,
    username   varchar(255) not null,
    password   varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint uk_users_username unique (username)
);

-- ---------------------------------------------------------------------------
-- friendships
-- ---------------------------------------------------------------------------
create table friendships (
    id           uuid         not null,
    user_one_id  uuid         not null,
    user_two_id  uuid         not null,
    initiator_id uuid         not null,
    status       varchar(255) not null,
    created_at   timestamp(6) with time zone not null,
    updated_at   timestamp(6) with time zone not null,
    primary key (id),
    constraint uk_friendship_pair unique (user_one_id, user_two_id)
);
create index idx_friendship_user_one on friendships (user_one_id);
create index idx_friendship_user_two on friendships (user_two_id);
create index idx_friendship_status   on friendships (status);

-- ---------------------------------------------------------------------------
-- conversations (DIRECT or GROUP)
-- ---------------------------------------------------------------------------
create table conversations (
    id                   uuid         not null,
    type                 varchar(255) not null,
    name                 varchar(255),
    created_by           uuid,
    dm_key               varchar(255),
    last_message_preview varchar(255),
    last_message_at      timestamp(6) with time zone,
    last_message_seq     bigint,
    created_at           timestamp(6) with time zone not null,
    updated_at           timestamp(6) with time zone not null,
    primary key (id),
    constraint uk_conversation_dm_key unique (dm_key)
);

-- ---------------------------------------------------------------------------
-- conversation_participants (membership + read/delivery watermarks)
-- ---------------------------------------------------------------------------
create table conversation_participants (
    id                 uuid         not null,
    conversation_id    uuid         not null,
    user_id            uuid         not null,
    role               varchar(255) not null,
    last_read_seq      bigint       not null,
    last_delivered_seq bigint       not null,
    muted              boolean      not null,
    joined_at          timestamp(6) with time zone not null,
    primary key (id),
    constraint uk_conversation_participant unique (conversation_id, user_id)
);
create index idx_cp_conversation on conversation_participants (conversation_id);
create index idx_cp_user         on conversation_participants (user_id);

-- ---------------------------------------------------------------------------
-- messages (unified; no per-message status/receiver — watermarks instead)
-- ---------------------------------------------------------------------------
create table messages (
    id                uuid          not null,
    conversation_id   uuid          not null,
    sender_id         uuid,
    client_message_id varchar(100),
    type              varchar(255)  not null,
    content           varchar(4000),
    sequence_number   bigint        not null,
    created_at        timestamp(6) with time zone not null,
    edited_at         timestamp(6) with time zone,
    deleted_at        timestamp(6) with time zone,
    primary key (id),
    constraint uk_message_sequence  unique (conversation_id, sequence_number),
    constraint uk_message_client_id unique (conversation_id, client_message_id)
);
create index idx_message_conversation_seq on messages (conversation_id, sequence_number);
create index idx_message_sender           on messages (sender_id);
create index idx_message_created          on messages (created_at);

-- ---------------------------------------------------------------------------
-- media_messages (detail row linked to a messages row of type=MEDIA)
-- ---------------------------------------------------------------------------
create table media_messages (
    id                 uuid          not null,
    message_id         uuid          not null,
    sender_id          uuid          not null,
    message_type       varchar(255)  not null,
    status             varchar(255)  not null,
    media_url          varchar(1024),
    thumbnail_url      varchar(1024),
    mime_type          varchar(100)  not null,
    file_size          bigint        not null,
    storage_key        varchar(255)  not null,
    original_file_name varchar(255),
    caption            varchar(1000),
    deleted            boolean       not null,
    created_at         timestamp(6)  not null,
    updated_at         timestamp(6)  not null,
    primary key (id)
);
create index idx_media_message on media_messages (message_id);
create index idx_media_sender  on media_messages (sender_id);
create index idx_media_status  on media_messages (status);
create index idx_media_created on media_messages (created_at);

-- ---------------------------------------------------------------------------
-- notifications
-- ---------------------------------------------------------------------------
create table notifications (
    id             uuid         not null,
    recipient_id   uuid         not null,
    actor_id       uuid,
    type           varchar(255) not null,
    reference_type varchar(255),
    reference_id   uuid,
    preview        varchar(280),
    event_count    integer      not null,
    read           boolean      not null,
    created_at     timestamp(6) with time zone not null,
    read_at        timestamp(6) with time zone,
    primary key (id)
);
create index idx_notif_recipient_created on notifications (recipient_id, created_at);
create index idx_notif_recipient_read    on notifications (recipient_id, read);

-- ---------------------------------------------------------------------------
-- outbox_events (transactional outbox)
-- ---------------------------------------------------------------------------
create table outbox_events (
    id             uuid        not null,
    aggregate_type varchar(64) not null,
    aggregate_id   uuid,
    event_type     varchar(64) not null,
    payload        text        not null,
    status         varchar(16) not null,
    created_at     timestamp(6) with time zone not null,
    published_at   timestamp(6) with time zone,
    primary key (id)
);
create index idx_outbox_status_created on outbox_events (status, created_at);
