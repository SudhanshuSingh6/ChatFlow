/** Mirrors the backend enums and response DTOs (Instants serialize as ISO strings). */

export type ConversationType = "DIRECT" | "GROUP";
export type ParticipantRole = "OWNER" | "ADMIN" | "MEMBER";
export type FriendshipStatus = "PENDING" | "ACCEPTED" | "DECLINED";
export type MessageType = "TEXT" | "MEDIA" | "SYSTEM";

export interface UserSummary {
  id: string;
  username: string;
}

export interface Participant {
  userId: string;
  username: string | null;
  role: ParticipantRole;
  joinedAt: string;
  lastReadSeq: number;
  lastDeliveredSeq: number;
}

/** Unified conversation — `summary` (list) and `detail` (single) share this shape. */
export interface Conversation {
  id: string;
  type: ConversationType;
  /** Group name; null for DIRECT. */
  name: string | null;
  /** Display title: group name, or the peer's username for DIRECT. */
  title: string | null;
  /** The other participant for DIRECT; null for GROUP. */
  peerId: string | null;
  createdBy: string | null;
  /** Null in list views; populated in detail. */
  participants: Participant[] | null;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  lastMessageSeq: number | null;
  unreadCount: number;
  callerRole: ParticipantRole;
  memberCount: number;
}

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  clientMessageId: string;
  type: MessageType;
  content: string;
  sequenceNumber: number;
  createdAt: string;
  editedAt: string | null;
  deleted: boolean;
  /** Present on MEDIA messages. */
  mediaId?: string | null;
  /** Populated once the media-processing pipeline finishes the thumbnail. */
  thumbnailUrl?: string | null;
  /** Original filename (e.g. "photo.jpg"). */
  originalFilename?: string | null;
  /** Broad category used for icon selection. */
  mediaType?: "IMAGE" | "VIDEO" | "DOCUMENT" | null;
}

/** A page of history plus the cursor for the next (older) page; null = no more. */
export interface MessagePage {
  messages: Message[];
  nextCursor: number | null;
}

export interface Friendship {
  id: string;
  otherUserId: string;
  otherUsername: string | null;
  initiatorId: string;
  status: FriendshipStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Presence {
  userId: string;
  online: boolean;
  onlineSince: string | null;
}

export interface ConversationPresence {
  participantOne: Presence | null;
  participantTwo: Presence | null;
}

export type NotificationType =
  | "FRIEND_REQUEST"
  | "FRIEND_REQUEST_ACCEPTED"
  | "GROUP_MEMBER_ADDED"
  | "GROUP_MEMBER_REMOVED"
  | "GROUP_ROLE_CHANGED"
  | "GROUP_OWNERSHIP_TRANSFERRED"
  | "NEW_MESSAGE";

export type ReferenceType = "FRIENDSHIP" | "CONVERSATION" | "MESSAGE";

export interface Notification {
  id: string;
  actorId: string;
  type: NotificationType;
  referenceType: ReferenceType;
  referenceId: string;
  preview: string | null;
  eventCount: number;
  read: boolean;
  createdAt: string;
}
