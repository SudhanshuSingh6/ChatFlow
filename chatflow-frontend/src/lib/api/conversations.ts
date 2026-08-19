import { apiClient } from "./client";
import type { Conversation, Message, MessagePage, Participant } from "../../types/domain";

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

/** Catch-up page; returns messages with sequenceNumber > `after`. */
export async function getMessagesAfter(
  id: string,
  after: number,
  limit = 50,
): Promise<MessagePage> {
  const { data } = await apiClient.get<MessagePage>(
    `/api/conversations/${id}/messages/after`,
    { params: { after, limit } },
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

export async function deleteGroup(id: string): Promise<void> {
  await apiClient.delete(`/api/conversations/${id}`);
}

export async function addParticipant(id: string, userId: string): Promise<Participant> {
  const { data } = await apiClient.post<Participant>(
    `/api/conversations/${id}/participants`,
    { userId },
  );
  return data;
}

export async function removeParticipant(id: string, userId: string): Promise<void> {
  await apiClient.delete(`/api/conversations/${id}/participants/${userId}`);
}

export async function updateMemberRole(
  id: string,
  userId: string,
  role: "ADMIN" | "MEMBER",
): Promise<Participant> {
  const { data } = await apiClient.put<Participant>(
    `/api/conversations/${id}/participants/${userId}/role`,
    { role },
  );
  return data;
}

export async function transferOwnership(
  id: string,
  newOwnerId: string,
): Promise<Conversation> {
  const { data } = await apiClient.post<Conversation>(
    `/api/conversations/${id}/transfer-ownership`,
    { newOwnerId },
  );
  return data;
}

export async function searchMessages(
  conversationId: string,
  query: string,
  limit = 20,
): Promise<Message[]> {
  const { data } = await apiClient.get<Message[]>("/api/messages/search", {
    params: { q: query, conversationId, limit },
  });
  return data;
}
