import { apiClient } from "./client";
import type { Notification } from "../../types/domain";

export async function getNotifications(cursor?: string, limit = 20): Promise<Notification[]> {
  const { data } = await apiClient.get<Notification[]>("/api/notifications", {
    params: { ...(cursor ? { cursor } : {}), limit },
  });
  return data;
}

export async function getUnreadCount(): Promise<{ count: number }> {
  const { data } = await apiClient.get<{ count: number }>("/api/notifications/unread-count");
  return data;
}

export async function markNotificationRead(id: string): Promise<void> {
  await apiClient.post(`/api/notifications/${id}/read`);
}

export async function markAllNotificationsRead(): Promise<void> {
  await apiClient.post("/api/notifications/read-all");
}

export async function deleteNotification(id: string): Promise<void> {
  await apiClient.delete(`/api/notifications/${id}`);
}
