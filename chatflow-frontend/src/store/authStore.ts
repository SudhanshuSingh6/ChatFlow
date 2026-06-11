import { create } from "zustand";
import { persist } from "zustand/middleware";

export interface AuthUser {
  userId: string;
  username: string;
}

interface AuthState {
  token: string | null;
  user: AuthUser | null;
  setAuth: (token: string, user: AuthUser) => void;
  clearAuth: () => void;
}

/**
 * Auth state is a Zustand store (not just React context) so it can be read
 * synchronously from outside React — the axios interceptor and the WebSocket
 * client both need the token without a hook. Persisted to localStorage so a
 * refresh keeps the session.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => set({ token, user }),
      clearAuth: () => set({ token: null, user: null }),
    }),
    { name: "chatflow-auth" },
  ),
);

/** Non-React accessors for the axios interceptor / WS client. */
export const getAuthToken = () => useAuthStore.getState().token;
export const clearAuth = () => useAuthStore.getState().clearAuth();
