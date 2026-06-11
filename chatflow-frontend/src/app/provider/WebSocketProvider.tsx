import {
  createContext,
  use,
  useEffect,
  useMemo,
  useRef,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "../../store/authStore";
import { useWsStore } from "../../store/wsStore";
import { WebSocketClient } from "../../lib/ws/WebSocketClient";
import { dispatchFrame } from "../../lib/ws/dispatcher";
import type { InboundType } from "../../lib/ws/types";

interface WebSocketContextValue {
  send: (type: InboundType, payload?: unknown) => string | undefined;
}

const WebSocketContext = createContext<WebSocketContextValue | null>(null);

/**
 * Owns the realtime socket for the session: opens it when authenticated, closes
 * on logout, routes inbound frames through the dispatcher, and exposes `send`.
 */
export default function WebSocketProvider({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  const currentUserId = useAuthStore((s) => s.user?.userId ?? "");
  const queryClient = useQueryClient();
  const setStatus = useWsStore((s) => s.setStatus);
  const clientRef = useRef<WebSocketClient | null>(null);

  useEffect(() => {
    if (!token) return;
    const client = new WebSocketClient({
      onFrame: (frame) => dispatchFrame(frame, { queryClient, currentUserId }),
      onStatus: setStatus,
    });
    clientRef.current = client;
    client.start(token);
    return () => {
      client.stop();
      clientRef.current = null;
    };
  }, [token, currentUserId, queryClient, setStatus]);

  const value = useMemo<WebSocketContextValue>(
    () => ({
      send: (type, payload) => clientRef.current?.send(type, payload),
    }),
    [],
  );

  return (
    <WebSocketContext value={value}>{children}</WebSocketContext>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useWebSocket() {
  const ctx = use(WebSocketContext);
  if (!ctx) {
    throw new Error("useWebSocket must be used within <WebSocketProvider>");
  }
  return ctx;
}
