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
          "flex items-center gap-3 border-b border-outline-variant p-4 transition-colors focus-visible:outline-none",
          isActive
            ? "bg-surface-container-low"
            : "hover:bg-surface-container cursor-pointer",
        )
      }
    >
      <div className="relative shrink-0">
        <Avatar name={name} size={48} />
        {online !== undefined && (
          <PresenceDot
            online={online}
            className="absolute right-0 bottom-0"
          />
        )}
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-2 mb-1">
          <span className="truncate text-sm font-semibold text-on-surface">{name}</span>
          <span
            className={cn(
              "shrink-0 text-xs",
              unread > 0 ? "text-primary font-semibold" : "text-on-surface-variant",
            )}
          >
            {formatListTime(time)}
          </span>
        </div>
        <div className="flex items-center justify-between gap-2">
          <span className="truncate text-xs text-on-surface-variant">
            {lastMessage ?? "No messages yet"}
          </span>
          <Badge count={unread} />
        </div>
      </div>
    </NavLink>
  );
}
