import { useMemo } from "react";
import { useMessages } from "./useConversations";
import { useWebSocket } from "../app/provider/WebSocketProvider";
import { useMessageStore } from "../store/messageStore";
import { useAuthStore } from "../store/authStore";
import type { Message } from "../types/domain";

const OPTIMISTIC_SEQ = Number.MAX_SAFE_INTEGER;

/**
 * Everything the conversation pane needs: merged history + live + optimistic
 * messages (deduped, ordered), receipt cursors for ticks, and a send action
 * that writes optimistically and emits SEND_MESSAGE over the socket.
 */
export function useChat(conversationId: string) {
  const currentUserId = useAuthStore((s) => s.user?.userId ?? "");
  const { send } = useWebSocket();

  const { messages: history, isLoading, hasNextPage, fetchNextPage, isFetchingNextPage } =
    useMessages(conversationId);

  const live = useMessageStore((s) => s.live[conversationId]);
  const optimistic = useMessageStore((s) => s.optimistic[conversationId]);
  const deliveredSeq = useMessageStore((s) => s.deliveredSeq[conversationId] ?? 0);
  const readSeq = useMessageStore((s) => s.readSeq[conversationId] ?? 0);
  const addOptimistic = useMessageStore((s) => s.addOptimistic);

  const messages = useMemo<Message[]>(() => {
    // History + live, deduped by id (live wins), then optimistic pinned last.
    const byId = new Map<string, Message>();
    for (const m of history) byId.set(m.id, m);
    for (const m of live ?? []) byId.set(m.id, m);
    const real = [...byId.values()].sort(
      (a, b) => a.sequenceNumber - b.sequenceNumber,
    );

    const pending: Message[] = (optimistic ?? []).map((o) => ({
      id: o.clientMessageId,
      conversationId: o.conversationId,
      senderId: o.senderId,
      clientMessageId: o.clientMessageId,
      type: "TEXT",
      content: o.content,
      sequenceNumber: OPTIMISTIC_SEQ,
      createdAt: o.createdAt,
      editedAt: null,
      deleted: false,
    }));

    return [...real, ...pending];
  }, [history, live, optimistic]);

  const maxSeq = useMemo(
    () =>
      messages.reduce(
        (max, m) =>
          m.sequenceNumber === OPTIMISTIC_SEQ ? max : Math.max(max, m.sequenceNumber),
        0,
      ),
    [messages],
  );

  const sendMessage = (content: string) => {
    const clientMessageId = crypto.randomUUID();
    addOptimistic({
      clientMessageId,
      conversationId,
      senderId: currentUserId,
      content,
      createdAt: new Date().toISOString(),
    });
    send("SEND_MESSAGE", { conversationId, clientMessageId, content });
  };

  return {
    messages,
    isLoading,
    hasNextPage,
    fetchNextPage,
    isFetchingNextPage,
    deliveredSeq,
    readSeq,
    maxSeq,
    sendMessage,
    currentUserId,
  };
}
