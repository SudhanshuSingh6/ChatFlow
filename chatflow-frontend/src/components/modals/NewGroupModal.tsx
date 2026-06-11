import { useState } from "react";
import { FiCheck } from "react-icons/fi";
import Modal from "./Modal";
import Avatar from "../ui/Avatar";
import { cn } from "../../lib/utils/cn";
import { useFriends } from "../../hooks/useFriends";
import { useCreateGroup } from "../../hooks/useConversations";
import { getErrorMessage } from "../../lib/api/client";

/** Name a group and pick friends to add (backend requires members to be friends). */
export default function NewGroupModal({ onClose }: { onClose: () => void }) {
  const { data: friends, isLoading } = useFriends();
  const createGroup = useCreateGroup();
  const [name, setName] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const toggle = (userId: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) next.delete(userId);
      else next.add(userId);
      return next;
    });
  };

  const submit = () => {
    if (!name.trim() || selected.size === 0) return;
    createGroup.mutate(
      { name: name.trim(), memberIds: [...selected] },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal title="New group" onClose={onClose}>
      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="Group name"
        className="mb-3 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/40"
      />

      {isLoading ? (
        <p className="py-6 text-center text-sm text-gray-400">Loading friends…</p>
      ) : !friends?.length ? (
        <p className="py-6 text-center text-sm text-gray-400">
          Add some friends first.
        </p>
      ) : (
        <ul className="max-h-64 space-y-1 overflow-y-auto">
          {friends.map((f) => {
            const isSelected = selected.has(f.otherUserId);
            return (
              <li key={f.id}>
                <button
                  type="button"
                  onClick={() => toggle(f.otherUserId)}
                  className="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left transition hover:bg-gray-50"
                >
                  <Avatar name={f.otherUsername ?? "?"} size={36} />
                  <span className="flex-1 font-medium text-gray-800">
                    {f.otherUsername ?? "Unknown"}
                  </span>
                  <span
                    className={cn(
                      "flex h-5 w-5 items-center justify-center rounded-full border",
                      isSelected
                        ? "bg-brand border-transparent text-white"
                        : "border-gray-300",
                    )}
                  >
                    {isSelected && <FiCheck className="text-xs" />}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {createGroup.isError && (
        <p className="mt-2 text-sm text-red-500">
          {getErrorMessage(createGroup.error)}
        </p>
      )}

      <button
        type="button"
        onClick={submit}
        disabled={!name.trim() || selected.size === 0 || createGroup.isPending}
        className="bg-brand mt-4 w-full rounded-lg px-4 py-2.5 font-semibold text-white transition hover:opacity-95 disabled:opacity-40"
      >
        {createGroup.isPending ? "Creating…" : `Create group (${selected.size})`}
      </button>
    </Modal>
  );
}
