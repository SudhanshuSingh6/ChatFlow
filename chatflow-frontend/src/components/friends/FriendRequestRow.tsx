import { FiCheck, FiX } from "react-icons/fi";
import Avatar from "../ui/Avatar";

interface FriendRequestRowProps {
  username: string;
  onAccept?: () => void;
  onDecline?: () => void;
}

export default function FriendRequestRow({
  username,
  onAccept,
  onDecline,
}: FriendRequestRowProps) {
  return (
    <div className="flex items-center gap-3 rounded-lg px-1 py-2">
      <Avatar name={username} size={36} />
      <span className="flex-1 text-sm font-semibold text-on-surface">{username}</span>
      <button
        type="button"
        aria-label="Accept"
        onClick={onAccept}
        className="flex h-8 w-8 items-center justify-center rounded-full bg-tertiary-container/20 text-tertiary-container transition hover:bg-tertiary-container/30"
      >
        <FiCheck />
      </button>
      <button
        type="button"
        aria-label="Decline"
        onClick={onDecline}
        className="flex h-8 w-8 items-center justify-center rounded-full bg-error-container/30 text-error transition hover:bg-error-container/50"
      >
        <FiX />
      </button>
    </div>
  );
}
