import type { QueryClient } from "@tanstack/react-query";
import { queryKeys } from "../../config/queryKeys";
import { useMessageStore } from "../../store/messageStore";
import { usePresenceStore } from "../../store/presenceStore";
import { useTypingStore } from "../../store/typingStore";
import type { Message } from "../../types/domain";
import type {
  Frame,
  MediaThumbnailReadyPayload,
  PresencePayload,
  SeenUpdatePayload,
  StatusUpdatePayload,
  TypingPayload,
} from "./types";

interface DispatchContext {
  queryClient: QueryClient;
  currentUserId: string;
}

/** Route an inbound frame to the relevant store and/or invalidate React Query caches. */
export function dispatchFrame(frame: Frame, ctx: DispatchContext) {
  const { queryClient, currentUserId } = ctx;
  const messages = useMessageStore.getState();

  switch (frame.type) {
    case "MESSAGE": {
      messages.addIncoming(frame.payload as Message);
      queryClient.invalidateQueries({ queryKey: queryKeys.conversations });
      break;
    }
    case "MESSAGE_ACK": {
      messages.ackMessage(frame.payload as Message);
      queryClient.invalidateQueries({ queryKey: queryKeys.conversations });
      break;
    }
    case "STATUS_UPDATE": {
      const p = frame.payload as StatusUpdatePayload;
      messages.setDelivered(p.conversationId, p.lastDeliveredSeq);
      break;
    }
    case "SEEN_UPDATE": {
      const p = frame.payload as SeenUpdatePayload;
      messages.setRead(p.conversationId, p.lastReadSeq);
      break;
    }
    case "PRESENCE": {
      const p = frame.payload as PresencePayload;
      usePresenceStore.getState().setPresence(p.userId, p.status === "ONLINE");
      break;
    }
    case "TYPING": {
      const p = frame.payload as TypingPayload;
      if (p.userId !== currentUserId) {
        useTypingStore.getState().setTyping(p.conversationId, p.userId, p.typing);
      }
      break;
    }
    case "FRIEND_REQUEST":
    case "FRIEND_REQUEST_ACCEPTED":
    case "FRIEND_REQUEST_DECLINED":
    case "FRIEND_REMOVED": {
      queryClient.invalidateQueries({ queryKey: queryKeys.friends });
      queryClient.invalidateQueries({
        queryKey: queryKeys.friendRequests("received"),
      });
      queryClient.invalidateQueries({
        queryKey: queryKeys.friendRequests("sent"),
      });
      break;
    }
    case "GROUP_CREATED":
    case "GROUP_MEMBER_ADDED":
    case "GROUP_MEMBER_REMOVED":
    case "GROUP_ROLE_CHANGED":
    case "GROUP_OWNERSHIP_TRANSFERRED":
    case "GROUP_DELETED": {
      queryClient.invalidateQueries({ queryKey: queryKeys.conversations });
      break;
    }
    case "MEDIA_MESSAGE": {
      messages.addIncoming(frame.payload as Message);
      queryClient.invalidateQueries({ queryKey: queryKeys.conversations });
      break;
    }
    case "MEDIA_THUMBNAIL_READY": {
      const p = frame.payload as MediaThumbnailReadyPayload;
      messages.updateMessage(p.messageId, p.conversationId, {
        thumbnailUrl: p.thumbnailUrl,
        mediaId: p.mediaId,
      });
      break;
    }
    case "NOTIFICATION":
    case "NOTIFICATION_READ": {
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications });
      queryClient.invalidateQueries({ queryKey: queryKeys.notificationUnreadCount });
      break;
    }
    default:
      break;
  }
}
