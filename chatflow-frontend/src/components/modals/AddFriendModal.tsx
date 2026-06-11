import { useState } from "react";
import Modal from "./Modal";
import { useSendFriendRequest } from "../../hooks/useFriends";
import { getErrorMessage } from "../../lib/api/client";

/** Send a friend request by username. */
export default function AddFriendModal({ onClose }: { onClose: () => void }) {
  const [username, setUsername] = useState("");
  const sendRequest = useSendFriendRequest();
  const [sent, setSent] = useState(false);

  const submit = () => {
    const trimmed = username.trim();
    if (trimmed.length < 3) return;
    sendRequest.mutate(trimmed, {
      onSuccess: () => {
        setSent(true);
        setUsername("");
      },
    });
  };

  return (
    <Modal title="Add friend" onClose={onClose}>
      <p className="mb-3 text-sm text-gray-500">
        Enter the username of the person you want to add.
      </p>
      <input
        value={username}
        onChange={(e) => {
          setUsername(e.target.value);
          setSent(false);
        }}
        onKeyDown={(e) => e.key === "Enter" && submit()}
        placeholder="Username"
        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/40"
      />

      {sendRequest.isError && (
        <p className="mt-2 text-sm text-red-500">
          {getErrorMessage(sendRequest.error)}
        </p>
      )}
      {sent && <p className="mt-2 text-sm text-green-600">Request sent!</p>}

      <button
        type="button"
        onClick={submit}
        disabled={username.trim().length < 3 || sendRequest.isPending}
        className="bg-brand mt-4 w-full rounded-lg px-4 py-2.5 font-semibold text-white transition hover:opacity-95 disabled:opacity-40"
      >
        {sendRequest.isPending ? "Sending…" : "Send request"}
      </button>
    </Modal>
  );
}
