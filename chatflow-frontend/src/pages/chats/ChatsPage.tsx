import { useState } from "react";
import { Outlet } from "react-router-dom";
import { FiEdit } from "react-icons/fi";
import ListPanel from "../../components/list/ListPanel";
import ConversationRow from "../../components/list/ConversationRow";
import IconButton from "../../components/ui/IconButton";
import { ConversationListSkeleton } from "../../components/ui/Skeleton";
import NewChatModal from "../../components/modals/NewChatModal";
import { useConversations } from "../../hooks/useConversations";

export default function ChatsPage() {
  const { data: conversations, isLoading, isError } = useConversations();
  const [showNewChat, setShowNewChat] = useState(false);

  const chats = (conversations ?? []).filter((c) => c.type === "DIRECT");

  return (
    <>
      <ListPanel
        title="Chats"
        searchPlaceholder="Search chats"
        actions={
          <IconButton aria-label="New chat" onClick={() => setShowNewChat(true)}>
            <FiEdit />
          </IconButton>
        }
      >
        {isLoading ? (
          <ConversationListSkeleton />
        ) : isError ? (
          <p className="px-4 py-6 text-center text-sm text-red-500">
            Couldn’t load chats.
          </p>
        ) : chats.length === 0 ? (
          <p className="px-4 py-6 text-center text-sm text-gray-400">
            No chats yet — start one with a friend.
          </p>
        ) : (
          chats.map((c) => (
            <ConversationRow
              key={c.id}
              to={`/chats/${c.id}`}
              name={c.title ?? "Direct message"}
              lastMessage={c.lastMessagePreview}
              time={c.lastMessageAt}
              unread={c.unreadCount}
            />
          ))
        )}
      </ListPanel>

      <Outlet />

      {showNewChat && <NewChatModal onClose={() => setShowNewChat(false)} />}
    </>
  );
}
