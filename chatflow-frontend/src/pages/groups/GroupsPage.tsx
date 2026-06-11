import { useState } from "react";
import { Outlet } from "react-router-dom";
import { FiPlus } from "react-icons/fi";
import ListPanel from "../../components/list/ListPanel";
import ConversationRow from "../../components/list/ConversationRow";
import IconButton from "../../components/ui/IconButton";
import { ConversationListSkeleton } from "../../components/ui/Skeleton";
import NewGroupModal from "../../components/modals/NewGroupModal";
import { useConversations } from "../../hooks/useConversations";

export default function GroupsPage() {
  const { data: conversations, isLoading, isError } = useConversations();
  const [showNewGroup, setShowNewGroup] = useState(false);

  const groups = (conversations ?? []).filter((c) => c.type === "GROUP");

  return (
    <>
      <ListPanel
        title="Groups"
        searchPlaceholder="Search groups"
        actions={
          <IconButton aria-label="New group" onClick={() => setShowNewGroup(true)}>
            <FiPlus />
          </IconButton>
        }
      >
        {isLoading ? (
          <ConversationListSkeleton />
        ) : isError ? (
          <p className="px-4 py-6 text-center text-sm text-red-500">
            Couldn’t load groups.
          </p>
        ) : groups.length === 0 ? (
          <p className="px-4 py-6 text-center text-sm text-gray-400">
            No groups yet — create one.
          </p>
        ) : (
          groups.map((g) => (
            <ConversationRow
              key={g.id}
              to={`/groups/${g.id}`}
              name={g.title ?? g.name ?? "Group"}
              lastMessage={g.lastMessagePreview}
              time={g.lastMessageAt}
              unread={g.unreadCount}
            />
          ))
        )}
      </ListPanel>

      <Outlet />

      {showNewGroup && <NewGroupModal onClose={() => setShowNewGroup(false)} />}
    </>
  );
}
