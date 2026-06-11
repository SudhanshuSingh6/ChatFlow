import { create } from "zustand";
import type { WsStatus } from "../lib/ws/WebSocketClient";

interface WsState {
  status: WsStatus;
  setStatus: (status: WsStatus) => void;
}

export const useWsStore = create<WsState>((set) => ({
  status: "closed",
  setStatus: (status) => set({ status }),
}));
