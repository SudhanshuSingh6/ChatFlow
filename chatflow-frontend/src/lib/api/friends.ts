import { apiClient } from "./client";
import type { Friendship } from "../../types/domain";

export async function listFriends(): Promise<Friendship[]> {
  const { data } = await apiClient.get<Friendship[]>("/api/friends");
  return data;
}

export async function listFriendRequests(
  box: "received" | "sent",
): Promise<Friendship[]> {
  const { data } = await apiClient.get<Friendship[]>(
    `/api/friends/requests/${box}`,
  );
  return data;
}

export async function sendFriendRequest(username: string): Promise<Friendship> {
  const { data } = await apiClient.post<Friendship>("/api/friends/requests", {
    username,
  });
  return data;
}

export async function acceptFriendRequest(
  friendshipId: string,
): Promise<Friendship> {
  const { data } = await apiClient.post<Friendship>(
    `/api/friends/requests/${friendshipId}/accept`,
  );
  return data;
}

export async function declineFriendRequest(
  friendshipId: string,
): Promise<Friendship> {
  const { data } = await apiClient.post<Friendship>(
    `/api/friends/requests/${friendshipId}/decline`,
  );
  return data;
}

export async function unfriend(userId: string): Promise<void> {
  await apiClient.delete(`/api/friends/${userId}`);
}
