import type { Message } from "../../types/domain";

/** Client → server frame types (mirror backend InboundMessage.Type). */
export type InboundType =
  | "SEND_MESSAGE"
  | "MESSAGE_DELIVERED"
  | "CONVERSATION_OPEN"
  | "MARK_READ"
  | "TYPING"
  | "PING";

/** Server → client frame types (mirror backend OutboundMessage.Type). */
export type OutboundType =
  | "MESSAGE"
  | "MESSAGE_ACK"
  | "STATUS_UPDATE"
  | "SEEN_UPDATE"
  | "PRESENCE"
  | "TYPING"
  | "ERROR"
  | "PONG"
  | "MEDIA_MESSAGE"
  | "MEDIA_THUMBNAIL_READY"
  | "GROUP_CREATED"
  | "GROUP_MEMBER_ADDED"
  | "GROUP_MEMBER_REMOVED"
  | "GROUP_ROLE_CHANGED"
  | "GROUP_OWNERSHIP_TRANSFERRED"
  | "GROUP_DELETED"
  | "FRIEND_REQUEST"
  | "FRIEND_REQUEST_ACCEPTED"
  | "FRIEND_REQUEST_DECLINED"
  | "FRIEND_REMOVED"
  | "NOTIFICATION"
  | "NOTIFICATION_READ";

/** Wire frame. Both directions share this envelope. */
export interface Frame<T = unknown> {
  type: string;
  requestId?: string;
  payload: T;
}

// ---- Outbound payload shapes (only the ones the client reacts to) ----

/** MESSAGE and MESSAGE_ACK both carry a full message. */
export type MessagePayload = Message;

export interface StatusUpdatePayload {
  conversationId: string;
  userId?: string;
  lastDeliveredSeq: number;
  updated?: boolean;
}

export interface SeenUpdatePayload {
  conversationId: string;
  userId: string;
  lastReadSeq: number;
}

export interface PresencePayload {
  userId: string;
  status: "ONLINE" | "OFFLINE";
  onlineSince: string | null;
}

export interface TypingPayload {
  conversationId: string;
  userId: string;
  typing: boolean;
}
