import { apiClient } from "./client";
import type { Message } from "../../types/domain";

export interface MediaUrlResponse {
  url: string;
}

export async function uploadMedia(
  conversationId: string,
  file: File,
  onProgress?: (pct: number) => void,
): Promise<Message> {
  const form = new FormData();
  form.append("conversationId", conversationId);
  form.append("file", file);

  const { data } = await apiClient.post<Message>("/api/messages/media", form, {
    headers: { "Content-Type": "multipart/form-data" },
    onUploadProgress: (e) => {
      if (onProgress && e.total) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    },
  });
  return data;
}

export async function getMediaUrl(mediaId: string): Promise<MediaUrlResponse> {
  const { data } = await apiClient.get<MediaUrlResponse>(
    `/api/messages/media/${mediaId}/url`,
  );
  return data;
}
