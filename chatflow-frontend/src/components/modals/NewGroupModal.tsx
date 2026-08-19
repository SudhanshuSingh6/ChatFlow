import { useState } from "react";
import { FiCheck } from "react-icons/fi";
import Modal from "./Modal";
import Avatar from "../ui/Avatar";
import Button from "../ui/Button";
import Input from "../ui/Input";
import { cn } from "../../lib/utils/cn";
import { useFriends } from "../../hooks/useFriends";
import { useCreateGroup } from "../../hooks/useConversations";
import { getErrorMessage } from "../../lib/api/client";

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
      <Input
        id="group-name"
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="Group name"
        className="mb-3"
      />

      {isLoading ? (
        <p className="py-6 text-center text-sm text-on-surface-variant">Loading friends…</p>
      ) : !friends?.length ? (
        <p className="py-6 text-center text-sm text-on-surface-variant">
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
                  className="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left transition hover:bg-surface-container"
                >
                  <Avatar name={f.otherUsername ?? "?"} size={36} />
                  <span className="flex-1 text-sm font-semibold text-on-surface">
                    {f.otherUsername ?? "Unknown"}
                  </span>
                  <span
                    className={cn(
                      "flex h-5 w-5 items-center justify-center rounded-full border",
                      isSelected
                        ? "border-transparent bg-primary text-on-primary"
                        : "border-outline-variant",
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
        <p className="mt-2 text-sm text-error">
          {getErrorMessage(createGroup.error)}
        </p>
      )}

      <Button
        fullWidth
        onClick={submit}
        isLoading={createGroup.isPending}
        disabled={!name.trim() || selected.size === 0}
        className="mt-4"
      >
        {createGroup.isPending ? "Creating…" : `Create group (${selected.size})`}
      </Button>
    </Modal>
  );
}
