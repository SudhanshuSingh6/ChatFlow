package com.chatflow.conversation.entity;

/**
 * A participant's role within a conversation. DIRECT conversations always use
 * MEMBER for both sides; roles only carry meaning for GROUP conversations.
 */
public enum ParticipantRole {
    OWNER,
    ADMIN,
    MEMBER
}
