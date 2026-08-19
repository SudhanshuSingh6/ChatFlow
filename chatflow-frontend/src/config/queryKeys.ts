/** Central React Query key factory so invalidations stay consistent. */
export const queryKeys = {
  me: ["me"] as const,
  conversations: ["conversations"] as const,
  conversation: (id: string) => ["conversations", id] as const,
  messages: (id: string) => ["conversations", id, "messages"] as const,
  conversationPresence: (id: string) => ["conversations", id, "presence"] as const,
  friends: ["friends"] as const,
  friendRequests: (box: "received" | "sent") =>
    ["friends", "requests", box] as const,
  userPresence: (userId: string) => ["users", userId, "presence"] as const,
  userSearch: (q: string) => ["users", "search", q] as const,
  notifications: ["notifications"] as const,
  notificationUnreadCount: ["notifications", "unread-count"] as const,
  messageSearch: (id: string, q: string) => ["messages", "search", id, q] as const,
};
