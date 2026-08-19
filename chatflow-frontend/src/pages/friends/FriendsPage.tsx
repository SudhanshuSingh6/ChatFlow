import { useState } from "react";
import { FiUserPlus } from "react-icons/fi";
import ListPanel from "../../components/list/ListPanel";
import IconButton from "../../components/ui/IconButton";
import FriendRow from "../../components/friends/FriendRow";
import FriendRequestRow from "../../components/friends/FriendRequestRow";
import EmptyState from "../EmptyState";
import AddFriendModal from "../../components/modals/AddFriendModal";
import { ConversationListSkeleton } from "../../components/ui/Skeleton";
import {
  useFriends,
  useFriendRequests,
  useRespondToRequest,
} from "../../hooks/useFriends";
import { useCreateDirect } from "../../hooks/useConversations";

export default function FriendsPage() {
  const { data: friends, isLoading, isError } = useFriends();
  const { data: received } = useFriendRequests("received");
  const respond = useRespondToRequest();
  const createDirect = useCreateDirect();
  const [showAdd, setShowAdd] = useState(false);

  return (
    <>
      <ListPanel
        title="Friends"
        searchPlaceholder="Search friends"
        actions={
          <IconButton aria-label="Add friend" onClick={() => setShowAdd(true)}>
            <FiUserPlus />
          </IconButton>
        }
      >
        {received && received.length > 0 && (
          <div className="px-3 pb-2">
            <p className="px-1 py-2 text-xs font-semibold tracking-wide text-on-surface-variant uppercase">
              Requests · {received.length}
            </p>
            {received.map((r) => (
              <FriendRequestRow
                key={r.id}
                username={r.otherUsername ?? "Unknown"}
                onAccept={() =>
                  respond.mutate({ friendshipId: r.id, action: "accept" })
                }
                onDecline={() =>
                  respond.mutate({ friendshipId: r.id, action: "decline" })
                }
              />
            ))}
          </div>
        )}

        <p className="px-4 py-2 text-xs font-semibold tracking-wide text-on-surface-variant uppercase">
          All friends
        </p>

        {isLoading ? (
          <ConversationListSkeleton rows={4} />
        ) : isError ? (
          <p className="px-4 py-6 text-center text-sm text-error">
            Couldn’t load friends.
          </p>
        ) : !friends?.length ? (
          <p className="px-4 py-6 text-center text-sm text-on-surface-variant">
            No friends yet — add someone.
          </p>
        ) : (
          friends.map((f) => (
            <FriendRow
              key={f.id}
              username={f.otherUsername ?? "Unknown"}
              onMessage={() => createDirect.mutate(f.otherUserId)}
            />
          ))
        )}
      </ListPanel>

      <EmptyState message="Select a friend to message them" />

      {showAdd && <AddFriendModal onClose={() => setShowAdd(false)} />}
    </>
  );
}
