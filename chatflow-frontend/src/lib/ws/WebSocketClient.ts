import type { Frame, InboundType } from "./types";

export type WsStatus = "connecting" | "open" | "closed";

interface WebSocketClientOptions {
  onFrame: (frame: Frame) => void;
  onStatus: (status: WsStatus) => void;
}

const HEARTBEAT_MS = 25_000;
const MAX_BACKOFF_MS = 15_000;

/** Resolve the /ws URL. Defaults to same-origin (the Vite dev proxy forwards it). */
function wsUrl(token: string): string {
  const override = import.meta.env.VITE_WS_URL as string | undefined;
  const base =
    override ??
    `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}`;
  return `${base.replace(/\/$/, "")}/ws?token=${encodeURIComponent(token)}`;
}

/**
 * Single auto-reconnecting WebSocket to the realtime service. Frames are the
 * generic `{type, requestId, payload}` envelope; `send` returns the requestId so
 * callers can correlate an ACK. PING/PONG keeps the socket warm.
 */
export class WebSocketClient {
  private ws: WebSocket | null = null;
  private token = "";
  private running = false;
  private attempts = 0;
  private heartbeat: ReturnType<typeof setInterval> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly opts: WebSocketClientOptions;

  constructor(opts: WebSocketClientOptions) {
    this.opts = opts;
  }

  start(token: string) {
    this.token = token;
    this.running = true;
    this.connect();
  }

  stop() {
    this.running = false;
    this.clearTimers();
    this.ws?.close();
    this.ws = null;
    this.opts.onStatus("closed");
  }

  /** Send a frame; returns its requestId for ACK correlation. */
  send(type: InboundType, payload?: unknown): string {
    const requestId = crypto.randomUUID();
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ type, requestId, payload: payload ?? {} }));
    }
    return requestId;
  }

  private connect() {
    if (!this.running) return;
    this.opts.onStatus("connecting");
    const ws = new WebSocket(wsUrl(this.token));
    this.ws = ws;

    ws.onopen = () => {
      this.attempts = 0;
      this.opts.onStatus("open");
      this.startHeartbeat();
    };

    ws.onmessage = (event) => {
      try {
        this.opts.onFrame(JSON.parse(event.data) as Frame);
      } catch {
        // Ignore malformed frames.
      }
    };

    ws.onclose = () => {
      this.stopHeartbeat();
      if (this.running) this.scheduleReconnect();
      else this.opts.onStatus("closed");
    };

    ws.onerror = () => ws.close();
  }

  private scheduleReconnect() {
    this.opts.onStatus("connecting");
    const delay = Math.min(1000 * 2 ** this.attempts, MAX_BACKOFF_MS);
    this.attempts += 1;
    this.reconnectTimer = setTimeout(() => this.connect(), delay);
  }

  private startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeat = setInterval(() => this.send("PING"), HEARTBEAT_MS);
  }

  private stopHeartbeat() {
    if (this.heartbeat) clearInterval(this.heartbeat);
    this.heartbeat = null;
  }

  private clearTimers() {
    this.stopHeartbeat();
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
  }
}
