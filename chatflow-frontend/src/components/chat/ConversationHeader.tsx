import { FiInfo, FiMoreVertical } from "react-icons/fi";
import Avatar from "../ui/Avatar";
import PresenceDot from "../presence/PresenceDot";
import IconButton from "../ui/IconButton";

interface ConversationHeaderProps {
  name: string;
  subtitle?: string;
  /** undefined => no presence dot (groups). */
  online?: boolean;
}

export default function ConversationHeader({
  name,
  subtitle,
  online,
}: ConversationHeaderProps) {
  return (
    <header className="flex items-center gap-3 border-b border-gray-200 bg-white px-5 py-3">
      <Avatar name={name} size={40} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2 font-semibold text-gray-900">
          <span className="truncate">{name}</span>
          {online !== undefined && <PresenceDot online={online} />}
        </div>
        {subtitle && <div className="text-xs text-gray-400">{subtitle}</div>}
      </div>
      <IconButton aria-label="Conversation info">
        <FiInfo />
      </IconButton>
      <IconButton aria-label="More">
        <FiMoreVertical />
      </IconButton>
    </header>
  );
}
