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
      <span className="flex-1 font-medium text-gray-800">{username}</span>
      <button
        type="button"
        aria-label="Accept"
        onClick={onAccept}
        className="flex h-8 w-8 items-center justify-center rounded-full bg-green-100 text-green-600 transition hover:bg-green-200"
      >
        <FiCheck />
      </button>
      <button
        type="button"
        aria-label="Decline"
        onClick={onDecline}
        className="flex h-8 w-8 items-center justify-center rounded-full bg-red-100 text-red-500 transition hover:bg-red-200"
      >
        <FiX />
      </button>
    </div>
  );
}
