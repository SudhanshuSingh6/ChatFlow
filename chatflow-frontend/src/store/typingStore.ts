import { create } from "zustand";

/** Safety auto-clear in case a "stopped typing" frame never arrives. */
const TYPING_TTL_MS = 6000;
const timers = new Map<string, ReturnType<typeof setTimeout>>();

interface TypingState {
  /** Typing user ids per conversation. */
  byConversation: Record<string, string[]>;
  setTyping: (conversationId: string, userId: string, typing: boolean) => void;
}

export const useTypingStore = create<TypingState>((set) => {
  const remove = (conversationId: string, userId: string) =>
    set((s) => ({
      byConversation: {
        ...s.byConversation,
        [conversationId]: (s.byConversation[conversationId] ?? []).filter(
          (id) => id !== userId,
        ),
      },
    }));

  return {
    byConversation: {},
    setTyping: (conversationId, userId, typing) => {
      const key = `${conversationId}:${userId}`;
      const pending = timers.get(key);
      if (pending) {
        clearTimeout(pending);
        timers.delete(key);
      }

      if (typing) {
        set((s) => {
          const current = s.byConversation[conversationId] ?? [];
          if (current.includes(userId)) return s;
          return {
            byConversation: {
              ...s.byConversation,
              [conversationId]: [...current, userId],
            },
          };
        });
        timers.set(
          key,
          setTimeout(() => {
            timers.delete(key);
            remove(conversationId, userId);
          }, TYPING_TTL_MS),
        );
      } else {
        remove(conversationId, userId);
      }
    },
  };
});
