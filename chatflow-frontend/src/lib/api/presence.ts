import { apiClient } from "./client";
import type { ConversationPresence, Presence } from "../../types/domain";

export async function getConversationPresence(
  conversationId: string,
): Promise<ConversationPresence> {
  const { data } = await apiClient.get<ConversationPresence>(
    `/api/conversations/${conversationId}/presence`,
  );
  return data;
}

export async function getUserPresence(userId: string): Promise<Presence> {
  const { data } = await apiClient.get<Presence>(
    `/api/users/${userId}/presence`,
  );
  return data;
}
