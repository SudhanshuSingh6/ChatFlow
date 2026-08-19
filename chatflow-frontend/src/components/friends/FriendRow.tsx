import { FiMessageCircle } from "react-icons/fi";
import Avatar from "../ui/Avatar";
import PresenceDot from "../presence/PresenceDot";

interface FriendRowProps {
  username: string;
  online?: boolean;
  onMessage?: () => void;
}

export default function FriendRow({ username, online, onMessage }: FriendRowProps) {
  return (
    <div className="flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-surface-container">
      <div className="relative">
        <Avatar name={username} size={36} />
        {online !== undefined && (
          <PresenceDot
            online={online}
            className="absolute right-0 bottom-0"
          />
        )}
      </div>
      <span className="flex-1 text-sm font-semibold text-on-surface">{username}</span>
      <button
        type="button"
        aria-label={`Message ${username}`}
        onClick={onMessage}
        className="rounded-full p-1.5 text-on-surface-variant transition-colors hover:bg-surface-container-high hover:text-primary focus-visible:ring-2 focus-visible:ring-primary/40 focus-visible:outline-none"
      >
        <FiMessageCircle />
      </button>
    </div>
  );
}
