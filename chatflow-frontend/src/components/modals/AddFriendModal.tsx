import { useState } from "react";
import Modal from "./Modal";
import Button from "../ui/Button";
import Input from "../ui/Input";
import { useSendFriendRequest } from "../../hooks/useFriends";
import { getErrorMessage } from "../../lib/api/client";

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
      <p className="mb-3 text-sm text-on-surface-variant">
        Enter the username of the person you want to add.
      </p>

      <Input
        id="add-friend-username"
        value={username}
        onChange={(e) => { setUsername(e.target.value); setSent(false); }}
        onKeyDown={(e) => e.key === "Enter" && submit()}
        placeholder="Username"
      />

      {sendRequest.isError && (
        <p className="mt-2 text-sm text-error">
          {getErrorMessage(sendRequest.error)}
        </p>
      )}
      {sent && <p className="mt-2 text-sm text-tertiary-container">Request sent!</p>}

      <Button
        fullWidth
        onClick={submit}
        isLoading={sendRequest.isPending}
        disabled={username.trim().length < 3}
        className="mt-4"
      >
        Send request
      </Button>
    </Modal>
  );
}
