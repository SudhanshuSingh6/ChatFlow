import { apiClient } from "./client";
import type { Conversation, MessagePage } from "../../types/domain";

export async function listConversations(): Promise<Conversation[]> {
  const { data } = await apiClient.get<Conversation[]>("/api/conversations");
  return data;
}

export async function getConversation(id: string): Promise<Conversation> {
  const { data } = await apiClient.get<Conversation>(`/api/conversations/${id}`);
  return data;
}

/** History page; `before` is a sequenceNumber cursor (omit for the newest page). */
export async function getMessages(
  id: string,
  before?: number,
  limit = 30,
): Promise<MessagePage> {
  const { data } = await apiClient.get<MessagePage>(
    `/api/conversations/${id}/messages`,
    { params: { ...(before != null ? { before } : {}), limit } },
  );
  return data;
}

export async function createDirect(userId: string): Promise<Conversation> {
  const { data } = await apiClient.post<Conversation>(
    "/api/conversations/direct",
    { userId },
  );
  return data;
}

export async function createGroup(
  name: string,
  memberIds: string[],
): Promise<Conversation> {
  const { data } = await apiClient.post<Conversation>(
    "/api/conversations/group",
    { name, memberIds },
  );
  return data;
}
