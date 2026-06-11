import { apiClient } from "./client";
import type { UserSummary } from "../../types/domain";

/** People-picker search (new chat / new group / add friend). */
export async function searchUsers(q: string, limit = 10): Promise<UserSummary[]> {
  const { data } = await apiClient.get<UserSummary[]>("/api/users/search", {
    params: { q, limit },
  });
  return data;
}

export async function getMe(): Promise<UserSummary> {
  const { data } = await apiClient.get<UserSummary>("/api/users/me");
  return data;
}
