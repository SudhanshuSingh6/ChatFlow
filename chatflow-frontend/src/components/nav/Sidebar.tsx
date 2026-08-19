import { useRef, useState, useEffect } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import {
  FiMessageCircle,
  FiUsers,
  FiUserCheck,
  FiEdit,
  FiSettings,
  FiLogOut,
  FiMoreVertical,
  FiBell,
  FiUserPlus,
  FiMessageSquare,
  FiX,
} from "react-icons/fi";
import { cn } from "../../lib/utils/cn";
import Avatar from "../ui/Avatar";
import Button from "../ui/Button";
import { useAuth } from "../../app/provider/AuthProvider";
import NewChatModal from "../modals/NewChatModal";
import {
  useNotifications,
  useNotificationUnreadCount,
  useMarkNotificationRead,
  useMarkAllRead,
  useDeleteNotification,
} from "../../hooks/useNotifications";
import type { Notification, NotificationType } from "../../types/domain";

const NAV_LINKS = [
  { to: "/chats",   label: "Chats",   Icon: FiMessageCircle },
  { to: "/friends", label: "Friends", Icon: FiUserCheck },
  { to: "/groups",  label: "Groups",  Icon: FiUsers },
];

function NavItem({
  to,
  label,
  Icon,
}: {
  to: string;
  label: string;
  Icon: React.ComponentType<{ className?: string }>;
}) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        cn(
          "flex items-center gap-3 rounded-lg px-4 py-3 text-sm font-semibold transition-colors duration-150 active:scale-[.98]",
          isActive
            ? "border-r-2 border-primary bg-secondary-container/30 text-primary"
            : "text-on-surface-variant hover:bg-surface-container-high",
        )
      }
    >
      <Icon className="text-lg" />
      <span>{label}</span>
    </NavLink>
  );
}

function notifIcon(type: NotificationType) {
  if (type === "FRIEND_REQUEST") return <FiUserPlus className="text-primary" />;
  if (type === "FRIEND_REQUEST_ACCEPTED") return <FiUserCheck className="text-tertiary" />;
  if (type === "NEW_MESSAGE") return <FiMessageSquare className="text-primary" />;
  return <FiUsers className="text-secondary" />;
}

function notifLabel(n: Notification): string {
  if (n.preview) return n.preview;
  switch (n.type) {
    case "FRIEND_REQUEST": return "Sent you a friend request";
    case "FRIEND_REQUEST_ACCEPTED": return "Accepted your friend request";
    case "GROUP_MEMBER_ADDED": return "You were added to a group";
    case "GROUP_MEMBER_REMOVED": return "You were removed from a group";
    case "GROUP_ROLE_CHANGED": return "Your group role was changed";
    case "GROUP_OWNERSHIP_TRANSFERRED": return "Group ownership transferred to you";
    case "NEW_MESSAGE": return "New message";
  }
}

function formatTime(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60_000);
  if (diffMins < 1) return "Just now";
  if (diffMins < 60) return `${diffMins}m ago`;
  const diffHrs = Math.floor(diffMins / 60);
  if (diffHrs < 24) return `${diffHrs}h ago`;
  return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function NotificationPanel({ onClose }: { onClose: () => void }) {
  const { data, isLoading } = useNotifications();
  const { mutate: markRead } = useMarkNotificationRead();
  const { mutate: markAll } = useMarkAllRead();
  const { mutate: remove } = useDeleteNotification();

  const notifications = data?.pages.flat() ?? [];

  return (
    <div className="absolute bottom-full left-0 right-0 mb-2 flex max-h-[420px] flex-col rounded-lg border border-outline-variant bg-surface-container-lowest shadow-lg">
      {/* Header */}
      <div className="flex shrink-0 items-center justify-between border-b border-outline-variant px-4 py-3">
        <span className="text-sm font-semibold text-on-surface">Notifications</span>
        <div className="flex items-center gap-2">
          {notifications.some((n) => !n.read) && (
            <button
              type="button"
              onClick={() => markAll()}
              className="text-xs text-primary hover:underline"
            >
              Mark all read
            </button>
          )}
          <button
            type="button"
            onClick={onClose}
            className="rounded p-0.5 text-on-surface-variant hover:bg-surface-container"
          >
            <FiX className="text-sm" />
          </button>
        </div>
      </div>

      {/* List */}
      <div className="overflow-y-auto">
        {isLoading && (
          <p className="px-4 py-6 text-center text-sm text-on-surface-variant">Loading…</p>
        )}
        {!isLoading && notifications.length === 0 && (
          <p className="px-4 py-6 text-center text-sm text-on-surface-variant">
            No notifications
          </p>
        )}
        {notifications.map((n) => (
          <div
            key={n.id}
            className={cn(
              "flex items-start gap-3 px-4 py-3 transition-colors",
              !n.read ? "bg-primary/[0.04]" : "hover:bg-surface-container-low",
            )}
          >
            <div className="mt-0.5 shrink-0 text-base">{notifIcon(n.type)}</div>
            <div className="min-w-0 flex-1">
              <p className={cn("text-sm leading-snug", !n.read ? "font-semibold text-on-surface" : "text-on-surface-variant")}>
                {notifLabel(n)}
              </p>
              <p className="mt-0.5 text-xs text-on-surface-variant">{formatTime(n.createdAt)}</p>
            </div>
            <div className="flex shrink-0 items-center gap-1">
              {!n.read && (
                <button
                  type="button"
                  title="Mark read"
                  onClick={() => markRead(n.id)}
                  className="h-2 w-2 rounded-full bg-primary hover:bg-primary-container"
                />
              )}
              <button
                type="button"
                title="Dismiss"
                onClick={() => remove(n.id)}
                className="rounded p-0.5 text-on-surface-variant opacity-0 transition-opacity hover:bg-surface-container group-hover:opacity-100"
              >
                <FiX className="text-xs" />
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default function Sidebar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);
  const [showNewChat, setShowNewChat] = useState(false);
  const notifRef = useRef<HTMLDivElement>(null);

  const { data: unreadData } = useNotificationUnreadCount();
  const unreadCount = unreadData?.count ?? 0;

  // Close notification panel on outside click
  useEffect(() => {
    if (!notifOpen) return;
    function handleClick(e: MouseEvent) {
      if (notifRef.current && !notifRef.current.contains(e.target as Node)) {
        setNotifOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [notifOpen]);

  return (
    <>
      <nav className="flex h-screen w-60 shrink-0 flex-col border-r border-outline-variant bg-surface py-2">
        {/* Brand */}
        <div className="px-6 py-4">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined fill-1 text-primary text-5xl">forum</span>
            <h1 className="text-xl font-bold text-primary">Chatflow</h1>
          </div>
        </div>

        {/* New Message CTA */}
        <div className="px-4 pb-4">
          <Button
            fullWidth
            onClick={() => setShowNewChat(true)}
            className="gap-2"
          >
            <FiEdit className="text-base" />
            New Message
          </Button>
        </div>

        {/* Nav links */}
        <div className="flex-1 overflow-y-auto px-3 space-y-0.5">
          {NAV_LINKS.map(({ to, label, Icon }) => (
            <NavItem key={to} to={to} label={label} Icon={Icon} />
          ))}
        </div>

        {/* Notifications */}
        <div className="border-t border-outline-variant px-3 pt-2" ref={notifRef}>
          <div className="relative">
            <button
              type="button"
              onClick={() => setNotifOpen((v) => !v)}
              className={cn(
                "flex w-full items-center gap-3 rounded-lg px-4 py-3 text-sm font-semibold transition-colors duration-150",
                notifOpen
                  ? "bg-surface-container text-on-surface"
                  : "text-on-surface-variant hover:bg-surface-container-high",
              )}
            >
              <div className="relative">
                <FiBell className="text-lg" />
                {unreadCount > 0 && (
                  <span className="absolute -right-1.5 -top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-bold text-on-primary">
                    {unreadCount > 99 ? "99+" : unreadCount}
                  </span>
                )}
              </div>
              <span>Notifications</span>
              {unreadCount > 0 && (
                <span className="ml-auto text-xs font-bold text-primary">{unreadCount}</span>
              )}
            </button>
            {notifOpen && <NotificationPanel onClose={() => setNotifOpen(false)} />}
          </div>
        </div>

        {/* User profile */}
        <div className="border-t border-outline-variant p-3">
          <div className="relative">
            <button
              type="button"
              onClick={() => setMenuOpen((v) => !v)}
              className="flex w-full items-center gap-3 rounded-lg p-2 transition-colors hover:bg-surface-container-high"
            >
              <Avatar name={user?.username ?? "?"} size={40} />
              <div className="min-w-0 flex-1 text-left">
                <p className="truncate text-sm font-semibold text-on-surface">
                  {user?.username ?? "You"}
                </p>
                <p className="text-xs text-tertiary-container">Online</p>
              </div>
              <FiMoreVertical className="shrink-0 text-on-surface-variant" />
            </button>

            {menuOpen && (
              <div
                className="absolute bottom-full left-0 mb-2 w-48 rounded-lg border border-outline-variant bg-surface-container-lowest shadow-lg"
                onBlur={() => setMenuOpen(false)}
              >
                <div className="py-1">
                  <button
                    type="button"
                    onClick={() => { setMenuOpen(false); navigate("/settings"); }}
                    className="flex w-full items-center gap-2 px-4 py-2 text-sm text-on-surface transition-colors hover:bg-surface-container"
                  >
                    <FiSettings />
                    Settings
                  </button>
                  <button
                    type="button"
                    onClick={() => { setMenuOpen(false); logout(); }}
                    className="flex w-full items-center gap-2 px-4 py-2 text-sm text-error transition-colors hover:bg-error-container/10"
                  >
                    <FiLogOut />
                    Logout
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </nav>

      {showNewChat && <NewChatModal onClose={() => setShowNewChat(false)} />}
    </>
  );
}
