import { create } from "zustand";
import type { Message } from "../types/domain";

/** A locally-created message awaiting its server ACK. */
export interface OptimisticMessage {
  clientMessageId: string;
  conversationId: string;
  senderId: string;
  content: string;
  createdAt: string;
}

interface MessageState {
  /** Server messages received live (recipient) or ACK'd (sender), per conversation. */
  live: Record<string, Message[]>;
  /** Pending optimistic sends, per conversation. */
  optimistic: Record<string, OptimisticMessage[]>;
  /** Highest seq others have delivered/read, per conversation (drives own-message ticks). */
  deliveredSeq: Record<string, number>;
  readSeq: Record<string, number>;

  addOptimistic: (msg: OptimisticMessage) => void;
  /** ACK of our own send: drop the optimistic entry and store the real message. */
  ackMessage: (msg: Message) => void;
  /** A message pushed to us (or replayed). */
  addIncoming: (msg: Message) => void;
  /** Patch fields on an existing live message (e.g. thumbnailUrl from MEDIA_THUMBNAIL_READY). */
  updateMessage: (messageId: string, conversationId: string, patch: Partial<Message>) => void;
  setDelivered: (conversationId: string, seq: number) => void;
  setRead: (conversationId: string, seq: number) => void;
}

function appendUnique(list: Message[] | undefined, msg: Message): Message[] {
  const existing = list ?? [];
  if (existing.some((m) => m.id === msg.id)) return existing;
  return [...existing, msg];
}

export const useMessageStore = create<MessageState>((set) => ({
  live: {},
  optimistic: {},
  deliveredSeq: {},
  readSeq: {},

  addOptimistic: (msg) =>
    set((s) => ({
      optimistic: {
        ...s.optimistic,
        [msg.conversationId]: [
          ...(s.optimistic[msg.conversationId] ?? []),
          msg,
        ],
      },
    })),

  ackMessage: (msg) =>
    set((s) => ({
      optimistic: {
        ...s.optimistic,
        [msg.conversationId]: (s.optimistic[msg.conversationId] ?? []).filter(
          (o) => o.clientMessageId !== msg.clientMessageId,
        ),
      },
      live: {
        ...s.live,
        [msg.conversationId]: appendUnique(s.live[msg.conversationId], msg),
      },
    })),

  addIncoming: (msg) =>
    set((s) => ({
      live: {
        ...s.live,
        [msg.conversationId]: appendUnique(s.live[msg.conversationId], msg),
      },
    })),

  updateMessage: (messageId, conversationId, patch) =>
    set((s) => ({
      live: {
        ...s.live,
        [conversationId]: (s.live[conversationId] ?? []).map((m) =>
          m.id === messageId ? { ...m, ...patch } : m,
        ),
      },
    })),

  setDelivered: (conversationId, seq) =>
    set((s) => ({
      deliveredSeq: {
        ...s.deliveredSeq,
        [conversationId]: Math.max(s.deliveredSeq[conversationId] ?? 0, seq),
      },
    })),

  setRead: (conversationId, seq) =>
    set((s) => ({
      readSeq: {
        ...s.readSeq,
        [conversationId]: Math.max(s.readSeq[conversationId] ?? 0, seq),
      },
    })),
}));
