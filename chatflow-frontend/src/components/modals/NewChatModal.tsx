import Modal from "./Modal";
import Avatar from "../ui/Avatar";
import { useFriends } from "../../hooks/useFriends";
import { useCreateDirect } from "../../hooks/useConversations";

/** Pick a friend to open (or reuse) a 1:1 conversation. */
export default function NewChatModal({ onClose }: { onClose: () => void }) {
  const { data: friends, isLoading } = useFriends();
  const createDirect = useCreateDirect();

  const open = (userId: string) =>
    createDirect.mutate(userId, { onSuccess: onClose });

  return (
    <Modal title="New chat" onClose={onClose}>
      {isLoading ? (
        <p className="py-6 text-center text-sm text-gray-400">Loading friends…</p>
      ) : !friends?.length ? (
        <p className="py-6 text-center text-sm text-gray-400">
          Add some friends first.
        </p>
      ) : (
        <ul className="max-h-80 space-y-1 overflow-y-auto">
          {friends.map((f) => (
            <li key={f.id}>
              <button
                type="button"
                disabled={createDirect.isPending}
                onClick={() => open(f.otherUserId)}
                className="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left transition hover:bg-gray-50 disabled:opacity-50"
              >
                <Avatar name={f.otherUsername ?? "?"} size={36} />
                <span className="font-medium text-gray-800">
                  {f.otherUsername ?? "Unknown"}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
}
