import {
  createContext,
  use,
  useCallback,
  useEffect,
  useMemo,
  type ReactNode,
} from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuthStore, type AuthUser } from "../../store/authStore";
import { getErrorMessage } from "../../lib/api/client";
import {
  loginRequest,
  registerRequest,
  type AuthResponse,
  type Credentials,
} from "../../lib/api/auth";
import { useMe } from "../../hooks/useMe";

interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  /** A request (login or register) is in flight. */
  isPending: boolean;
  /** Last auth error message, or null. */
  error: string | null;
  login: (creds: Credentials) => Promise<void>;
  register: (creds: Credentials) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export default function AuthProvider({ children }: { children: ReactNode }) {
  const user = useAuthStore((s) => s.user);
  const token = useAuthStore((s) => s.token);
  const setAuth = useAuthStore((s) => s.setAuth);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const queryClient = useQueryClient();

  const onAuthSuccess = useCallback(
    (res: AuthResponse) =>
      setAuth(res.token, { userId: res.userId, username: res.username }),
    [setAuth],
  );

  // Keep the profile in sync with the server (handles page reloads where the
  // token is already in localStorage but the profile might be stale).
  const { data: me } = useMe();
  useEffect(() => {
    if (me && token) {
      setAuth(token, { userId: me.id, username: me.username });
    }
  }, [me, token, setAuth]);

  const loginMutation = useMutation({
    mutationFn: loginRequest,
    onSuccess: onAuthSuccess,
  });

  const registerMutation = useMutation({
    mutationFn: registerRequest,
    onSuccess: onAuthSuccess,
  });

  const login = useCallback(
    async (creds: Credentials) => {
      await loginMutation.mutateAsync(creds);
    },
    [loginMutation],
  );

  const register = useCallback(
    async (creds: Credentials) => {
      await registerMutation.mutateAsync(creds);
    },
    [registerMutation],
  );

  const logout = useCallback(() => {
    clearAuth();
    // Drop all cached server state so the next user starts clean.
    queryClient.clear();
  }, [clearAuth, queryClient]);

  const activeError = loginMutation.error ?? registerMutation.error;

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: Boolean(token),
      isPending: loginMutation.isPending || registerMutation.isPending,
      error: activeError ? getErrorMessage(activeError) : null,
      login,
      register,
      logout,
    }),
    [
      user,
      token,
      loginMutation.isPending,
      registerMutation.isPending,
      activeError,
      login,
      register,
      logout,
    ],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}

/** Access auth state and actions. Must be used within <AuthProvider>. */
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = use(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within <AuthProvider>");
  }
  return ctx;
}
