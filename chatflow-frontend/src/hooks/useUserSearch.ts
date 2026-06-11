import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "../config/queryKeys";
import { searchUsers } from "../lib/api/users";

/** Debounced user search for people-pickers. Runs only for queries of 2+ chars. */
export function useUserSearch(query: string, delay = 300) {
  const [debounced, setDebounced] = useState(query);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(query.trim()), delay);
    return () => clearTimeout(t);
  }, [query, delay]);

  return useQuery({
    queryKey: queryKeys.userSearch(debounced),
    queryFn: () => searchUsers(debounced),
    enabled: debounced.length >= 2,
  });
}
