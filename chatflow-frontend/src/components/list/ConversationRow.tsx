import { NavLink } from "react-router-dom";
import Avatar from "../ui/Avatar";
import Badge from "../ui/Badge";
import PresenceDot from "../presence/PresenceDot";
import { formatListTime } from "../../lib/utils/time";
import { cn } from "../../lib/utils/cn";

interface ConversationRowProps {
  to: string;
  name: string;
  lastMessage: string | null;
  time: string | null;
  unread?: number;
  /** undefined => no presence dot (e.g. groups). */
  online?: boolean;
}

export default function ConversationRow({
  to,
  name,
  lastMessage,
  time,
  unread = 0,
  online,
}: ConversationRowProps) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        cn(
          "flex items-center gap-3 px-3 py-3 transition focus-visible:bg-gray-50 focus-visible:outline-none focus-visible:-outline-offset-2 focus-visible:outline-blue-500",
          isActive ? "bg-blue-50" : "hover:bg-gray-50",
        )
      }
    >
      <div className="relative">
        <Avatar name={name} />
        {online !== undefined && (
          <PresenceDot
            online={online}
            className="absolute right-0 bottom-0 ring-2 ring-white"
          />
        )}
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <span className="truncate font-semibold text-gray-900">{name}</span>
          <span className="shrink-0 text-xs text-gray-400">
            {formatListTime(time)}
          </span>
        </div>
        <div className="flex items-center justify-between gap-2">
          <span className="truncate text-sm text-gray-500">
            {lastMessage ?? "No messages yet"}
          </span>
          <Badge count={unread} />
        </div>
      </div>
    </NavLink>
  );
}
