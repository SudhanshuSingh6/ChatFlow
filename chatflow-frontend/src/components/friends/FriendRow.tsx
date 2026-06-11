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
    <div className="flex items-center gap-3 px-4 py-2.5 transition hover:bg-gray-50">
      <div className="relative">
        <Avatar name={username} size={36} />
        {online !== undefined && (
          <PresenceDot
            online={online}
            className="absolute right-0 bottom-0 ring-2 ring-white"
          />
        )}
      </div>
      <span className="flex-1 font-medium text-gray-800">{username}</span>
      <button
        type="button"
        aria-label={`Message ${username}`}
        onClick={onMessage}
        className="rounded-full p-1 text-gray-400 transition hover:text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none"
      >
        <FiMessageCircle />
      </button>
    </div>
  );
}
