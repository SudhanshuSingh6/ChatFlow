import { create } from "zustand";

interface PresenceState {
  online: Record<string, boolean>;
  setPresence: (userId: string, online: boolean) => void;
  /** Seed from a REST snapshot without clobbering live updates. */
  seed: (entries: Record<string, boolean>) => void;
}

export const usePresenceStore = create<PresenceState>((set) => ({
  online: {},
  setPresence: (userId, online) =>
    set((s) => ({ online: { ...s.online, [userId]: online } })),
  seed: (entries) => set((s) => ({ online: { ...entries, ...s.online } })),
}));
