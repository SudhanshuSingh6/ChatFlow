import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "../../config/queryKeys";
import type { Conversation } from "../../types/domain";
import ConversationHeader from "./ConversationHeader";
import GroupSettingsPanel from "./GroupSettingsPanel";
import MessageSearchPanel from "./MessageSearchPanel";
import MessageList from "./MessageList";
import MessageInput from "./MessageInput";
import { MessageListSkeleton } from "../ui/Skeleton";
import EmptyState from "../../pages/EmptyState";
import {
  useConversation,
  useConversationPresence,
} from "../../hooks/useConversations";
import { useChat } from "../../hooks/useChat";
import { useWebSocket } from "../../app/provider/WebSocketProvider";
import { usePresenceStore } from "../../store/presenceStore";
import { useTypingStore } from "../../store/typingStore";
import { useWsStore } from "../../store/wsStore";

/**
 * Shared conversation pane for both DIRECT (/chats/:id) and GROUP (/groups/:id).
 * Merges REST history with live socket messages, drives receipts/typing/presence.
 */
export default function ConversationView({ id }: { id: string }) {
  const { send } = useWebSocket();
  const queryClient = useQueryClient();
  const wsOpen = useWsStore((s) => s.status === "open");
  const { data: conversation, isLoading, isError } = useConversation(id);
  const isDirect = conversation?.type === "DIRECT";
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [highlightedSeq, setHighlightedSeq] = useState<number | undefined>();
  const highlightTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const {
    messages,
    isLoading: messagesLoading,
    hasNextPage,
    fetchNextPage,
    isFetchingNextPage,
    deliveredSeq,
    readSeq,
    maxSeq,
    sendMessage,
    currentUserId,
  } = useChat(id);

  const { data: presenceSnapshot } = useConversationPresence(id, isDirect);
  const seedPresence = usePresenceStore((s) => s.seed);
  const presenceMap = usePresenceStore((s) => s.online);
  const typingUserIds = useTypingStore((s) => s.byConversation[id]) ?? [];

  // Clear search when switching conversations.
  useEffect(() => {
    setSearchOpen(false);
    setSearchQuery("");
    setHighlightedSeq(undefined);
    return () => { if (highlightTimerRef.current) clearTimeout(highlightTimerRef.current); };
  }, [id]);

  function handleSelectResult(seq: number) {
    setHighlightedSeq(seq);
    setSearchOpen(false);
    setSearchQuery("");
    if (highlightTimerRef.current) clearTimeout(highlightTimerRef.current);
    highlightTimerRef.current = setTimeout(() => setHighlightedSeq(undefined), 2500);
  }

  // Tell the server the conversation is open (marks delivered) once per mount.
  useEffect(() => {
    if (wsOpen) send("CONVERSATION_OPEN", { conversationId: id });
  }, [id, wsOpen, send]);

  // Seed presence from the REST snapshot; live PRESENCE frames take over after.
  useEffect(() => {
    if (!presenceSnapshot) return;
    const entries: Record<string, boolean> = {};
    for (const p of [presenceSnapshot.participantOne, presenceSnapshot.participantTwo]) {
      if (p) entries[p.userId] = p.online;
    }
    seedPresence(entries);
  }, [presenceSnapshot, seedPresence]);

  // Mark read up to the newest message whenever it advances while viewing, and
  // optimistically clear this conversation's unread badge (the server only pushes
  // read receipts to other participants, not back to us).
  useEffect(() => {
    if (!wsOpen || maxSeq <= 0) return;
    send("MARK_READ", { conversationId: id, upToSeq: maxSeq });
    queryClient.setQueryData<Conversation[]>(queryKeys.conversations, (prev) =>
      prev?.map((c) => (c.id === id ? { ...c, unreadCount: 0 } : c)),
    );
  }, [id, maxSeq, wsOpen, send, queryClient]);

  if (isLoading) {
    return (
      <main className="flex flex-1 flex-col">
        <div className="h-16 border-b border-outline-variant bg-surface-container-lowest" />
        <MessageListSkeleton />
      </main>
    );
  }

  if (isError || !conversation) {
    return <EmptyState message="Conversation not found" />;
  }

  const title = conversation.title ?? (isDirect ? "Direct message" : "Group");
  const participants = conversation.participants ?? [];
  const senderNames = Object.fromEntries(
    participants.map((p) => [p.userId, p.username ?? "Unknown"]),
  );

  let online: boolean | undefined;
  let subtitle: string | undefined;
  if (isDirect && conversation.peerId) {
    online = presenceMap[conversation.peerId] ?? false;
    subtitle = online ? "online" : "offline";
  } else {
    subtitle = `${conversation.memberCount} members`;
  }

  // Name(s) of others currently typing.
  const typingNames = typingUserIds
    .filter((uid) => uid !== currentUserId)
    .map((uid) => senderNames[uid] ?? "Someone");
  const typingName = typingNames.length ? typingNames[0] : undefined;

  return (
    <main className="relative flex flex-1 flex-col">
      <ConversationHeader
        name={isDirect ? title : `# ${title}`}
        online={online}
        subtitle={subtitle}
        isGroup={!isDirect}
        onSettingsClick={() => setSettingsOpen((v) => !v)}
        searchOpen={searchOpen}
        onSearchToggle={() => { setSearchOpen((v) => !v); setSearchQuery(""); }}
        searchQuery={searchQuery}
        onSearchQueryChange={setSearchQuery}
      />
      {searchOpen && (
        <MessageSearchPanel
          conversationId={id}
          query={searchQuery}
          senderNames={senderNames}
          currentUserId={currentUserId}
          onSelect={handleSelectResult}
        />
      )}
      {settingsOpen && !isDirect && (
        <GroupSettingsPanel
          conversation={conversation}
          onClose={() => setSettingsOpen(false)}
        />
      )}

      {messagesLoading ? (
        <MessageListSkeleton />
      ) : (
        <div className="flex flex-1 flex-col overflow-hidden">
          {hasNextPage && (
            <button
              type="button"
              onClick={() => fetchNextPage()}
              disabled={isFetchingNextPage}
              className="mx-auto my-2 rounded-full bg-surface-container px-3 py-1 text-xs text-on-surface-variant transition hover:bg-surface-container-high disabled:opacity-50"
            >
              {isFetchingNextPage ? "Loading…" : "Load earlier messages"}
            </button>
          )}
          <MessageList
            messages={messages}
            currentUserId={currentUserId}
            senderNames={isDirect ? undefined : senderNames}
            deliveredSeq={deliveredSeq}
            readSeq={readSeq}
            typingName={typingName}
            highlightedSeq={highlightedSeq}
          />
        </div>
      )}

      <MessageInput
        onSend={sendMessage}
        onTyping={(typing) => send("TYPING", { conversationId: id, typing })}
        placeholder={`Message ${title}…`}
        disabled={!wsOpen}
        conversationId={id}
      />
    </main>
  );
}
