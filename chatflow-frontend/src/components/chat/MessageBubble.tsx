import { FiCheck, FiFile, FiFilm, FiImage, FiLoader } from "react-icons/fi";
import { cn } from "../../lib/utils/cn";
import { formatMessageTime } from "../../lib/utils/time";
import Avatar from "../ui/Avatar";
import type { MessageType } from "../../types/domain";

export type MessageStatus = "SENT" | "DELIVERED" | "SEEN";

interface MessageBubbleProps {
  content: string;
  createdAt: string;
  mine: boolean;
  status?: MessageStatus;
  senderLabel?: string;
  /** Avatar shown left of received messages when firstOfGroup is true. */
  senderName?: string;
  firstOfGroup?: boolean;
  messageType?: MessageType;
  thumbnailUrl?: string | null;
  mediaId?: string | null;
  mediaType?: "IMAGE" | "VIDEO" | "DOCUMENT" | null;
  originalFilename?: string | null;
  onMediaClick?: () => void;
}

function Ticks({ status }: { status: MessageStatus }) {
  if (status === "SENT") return <FiCheck className="text-[11px]" />;
  return (
    <span className={cn("flex", status === "SEEN" && "text-primary-fixed-dim")}>
      <FiCheck className="-mr-[5px] text-[11px]" />
      <FiCheck className="text-[11px]" />
    </span>
  );
}

function MediaContent({
  thumbnailUrl,
  mediaType,
  originalFilename,
  onMediaClick,
  mine,
}: {
  thumbnailUrl?: string | null;
  mediaType?: "IMAGE" | "VIDEO" | "DOCUMENT" | null;
  originalFilename?: string | null;
  onMediaClick?: () => void;
  mine: boolean;
}) {
  const name = originalFilename ?? "media";

  if (thumbnailUrl) {
    return (
      <button
        type="button"
        onClick={onMediaClick}
        className="block overflow-hidden rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        <img
          src={thumbnailUrl}
          alt={name}
          className="max-h-48 max-w-[240px] rounded-md object-cover"
          loading="lazy"
        />
        {originalFilename && (
          <p className={cn("mt-1 text-xs truncate max-w-[240px]", mine ? "text-on-primary/70" : "text-on-surface-variant")}>
            {originalFilename}
          </p>
        )}
      </button>
    );
  }

  const Icon =
    mediaType === "IMAGE" ? FiImage :
    mediaType === "VIDEO" ? FiFilm :
    mediaType === "DOCUMENT" ? FiFile :
    FiLoader;

  return (
    <button
      type="button"
      onClick={onMediaClick}
      className={cn(
        "flex items-center gap-2 rounded-md px-2 py-1.5 text-sm",
        mine ? "text-on-primary/80" : "text-on-surface-variant",
        onMediaClick && "hover:underline",
      )}
    >
      <Icon className="shrink-0" />
      <span className="max-w-[200px] truncate">{name}</span>
      {mediaType === null && <span className="text-xs opacity-60">Processing…</span>}
    </button>
  );
}

export default function MessageBubble({
  content,
  createdAt,
  mine,
  status,
  senderLabel,
  senderName,
  firstOfGroup = true,
  messageType,
  thumbnailUrl,
  mediaType,
  originalFilename,
  onMediaClick,
}: MessageBubbleProps) {
  const isMedia = messageType === "MEDIA";

  return (
    <div
      className={cn(
        "animate-msg-in flex gap-3",
        firstOfGroup ? "mt-4" : "mt-1",
        mine ? "flex-row-reverse" : "flex-row",
      )}
    >
      {/* Avatar placeholder — keeps bubble aligned when avatar is hidden */}
      <div className="w-8 shrink-0">
        {!mine && firstOfGroup && senderName && (
          <Avatar name={senderName} size={32} />
        )}
      </div>

      <div className={cn("flex max-w-[70%] flex-col", mine && "items-end")}>
        {!mine && firstOfGroup && senderLabel && (
          <span className="mb-1 text-xs font-semibold text-primary">
            {senderLabel}
          </span>
        )}

        <div
          className={cn(
            "rounded-lg shadow-sm",
            isMedia ? "p-1" : "px-3 py-2 text-sm",
            mine
              ? "bg-primary text-on-primary rounded-br-none"
              : "rounded-bl-none border border-outline-variant bg-surface-container-lowest text-on-surface",
          )}
        >
          {isMedia ? (
            <MediaContent
              thumbnailUrl={thumbnailUrl}
              mediaType={mediaType}
              originalFilename={originalFilename}
              onMediaClick={onMediaClick}
              mine={mine}
            />
          ) : (
            <p className="break-words whitespace-pre-wrap">{content}</p>
          )}
        </div>

        <div
          className={cn(
            "mt-1 flex items-center gap-1 text-[10px] text-on-surface-variant",
            mine && "flex-row-reverse",
          )}
        >
          <span>{formatMessageTime(createdAt)}</span>
          {mine && status && <Ticks status={status} />}
        </div>
      </div>
    </div>
  );
}
