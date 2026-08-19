import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "../config/queryKeys";
import { getMe } from "../lib/api/users";
import { useAuthStore } from "../store/authStore";

export function useMe() {
  const token = useAuthStore((s) => s.token);
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: getMe,
    enabled: Boolean(token),
    staleTime: 5 * 60 * 1000,
  });
}
