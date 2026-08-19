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
import { useMessageStore } from "../../store/messageStore";
import { WebSocketClient } from "../../lib/ws/WebSocketClient";
import { dispatchFrame } from "../../lib/ws/dispatcher";
import { getMessagesAfter } from "../../lib/api/conversations";
import { queryKeys } from "../../config/queryKeys";
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
  // Tracks whether the socket has successfully opened at least once in this session.
  // Used to distinguish first connection from a reconnect.
  const hasConnectedRef = useRef(false);

  useEffect(() => {
    if (!token) return;
    hasConnectedRef.current = false;

    const client = new WebSocketClient({
      onFrame: (frame) => dispatchFrame(frame, { queryClient, currentUserId }),
      onStatus: (status) => {
        if (status === "open") {
          if (hasConnectedRef.current) {
            // Reconnect: invalidate conversation list so unread counts refresh,
            // then replay any messages missed during the gap.
            queryClient.invalidateQueries({ queryKey: queryKeys.conversations });
            const live = useMessageStore.getState().live;
            for (const [conversationId, messages] of Object.entries(live)) {
              const maxSeq = Math.max(...messages.map((m) => m.sequenceNumber), 0);
              if (maxSeq > 0) {
                getMessagesAfter(conversationId, maxSeq).then(({ messages: missed }) => {
                  missed.forEach((m) => useMessageStore.getState().addIncoming(m));
                }).catch(() => {
                  // Best-effort — full reload will catch it on next open.
                });
              }
            }
          }
          hasConnectedRef.current = true;
        }
        setStatus(status);
      },
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
