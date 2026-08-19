import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "../config/queryKeys";
import { searchMessages } from "../lib/api/conversations";

export function useMessageSearch(
  conversationId: string,
  query: string,
  delay = 300,
) {
  const [debounced, setDebounced] = useState(query);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(query.trim()), delay);
    return () => clearTimeout(t);
  }, [query, delay]);

  return useQuery({
    queryKey: queryKeys.messageSearch(conversationId, debounced),
    queryFn: () => searchMessages(conversationId, debounced),
    enabled: debounced.length >= 2,
  });
}
