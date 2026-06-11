import { useState, type ReactNode } from "react";
import {
  QueryClient,
  QueryClientProvider,
  type DefaultOptions,
} from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";

/**
 * Defaults tuned for ChatFlow: realtime updates arrive over the WebSocket, so
 * REST queries don't need aggressive polling. We keep data fresh for a short
 * window and avoid refetch storms on window focus.
 */
const defaultOptions: DefaultOptions = {
  queries: {
    // Realtime state comes from the WS; treat REST reads as fresh for 30s.
    staleTime: 30_000,
    // Keep unused data around for 5 min so navigating back is instant.
    gcTime: 5 * 60_000,
    // The WS already pushes live updates — no need to refetch on every focus.
    refetchOnWindowFocus: false,
    retry: 2,
  },
  mutations: {
    retry: 0,
  },
};

// eslint-disable-next-line react-refresh/only-export-components
export function createQueryClient() {
  return new QueryClient({ defaultOptions });
}

export default function QueryProvider({ children }: { children: ReactNode }) {
  // One client per app instance; useState ensures it survives re-renders and
  // isn't shared across requests (important if this ever runs under SSR).
  const [queryClient] = useState(createQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      {children}
      {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
    </QueryClientProvider>
  );
}
