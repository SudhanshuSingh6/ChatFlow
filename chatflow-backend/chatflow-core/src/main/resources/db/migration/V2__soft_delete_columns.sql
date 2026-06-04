-- ---------------------------------------------------------------------------
-- Soft-delete support for conversations (GROUP) and notifications.
--
-- `messages.deleted_at` already exists from V1. These columns let group deletion
-- and notification dismissal become soft deletes (hidden immediately); a daily
-- cleanup job hard-purges rows whose deleted_at is older than the retention window.
-- ---------------------------------------------------------------------------
alter table conversations add column deleted_at timestamp(6) with time zone;
alter table notifications  add column deleted_at timestamp(6) with time zone;

-- Indexes support the scheduled purge scans (WHERE deleted_at < cutoff).
create index idx_conversation_deleted on conversations (deleted_at);
create index idx_notification_deleted on notifications (deleted_at);
