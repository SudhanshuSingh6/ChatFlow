package com.chatflow.infra.outbox;

/**
 * Canonical {@code event_type} values written to {@code outbox_events}. Kept as
 * constants (not an enum) so the column can absorb new event kinds without a
 * schema/enum migration; the dispatcher routes on these.
 */
public final class OutboxEventType {

    private OutboxEventType() {
    }

    public static final String MESSAGE_CREATED = "message.created";
    public static final String MESSAGE_EMBEDDING_REQUESTED = "message.embedding_requested";
    public static final String FRIEND_REQUESTED = "friend.requested";
    public static final String FRIEND_REQUEST_ACCEPTED = "friend.request_accepted";
    public static final String GROUP_MEMBER_ADDED = "group.member_added";
    public static final String GROUP_MEMBER_REMOVED = "group.member_removed";
    public static final String GROUP_ROLE_CHANGED = "group.role_changed";
    public static final String GROUP_OWNERSHIP_TRANSFERRED = "group.ownership_transferred";
}
