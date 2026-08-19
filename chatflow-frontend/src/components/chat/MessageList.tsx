import { useEffect, useRef, type ReactNode } from "react";
import MessageBubble from "./MessageBubble";
import DateDivider from "./DateDivider";
import TypingDots from "./TypingDots";
import { formatDateDivider } from "../../lib/utils/time";
import type { Message } from "../../types/domain";
import { getMediaUrl } from "../../lib/api/media";

interface MessageListProps {
  messages: Message[];
  currentUserId: string;
  typingName?: string;
  /** Map senderId -> display name, for group sender labels. */
  senderNames?: Record<string, string>;
  /** Highest seq others have delivered/read — drives own-message ticks. */
  deliveredSeq?: number;
  readSeq?: number;
  /** Sequence number to scroll to and briefly highlight. */
  highlightedSeq?: number;
}

export default function MessageList({
  messages,
  currentUserId,
  typingName,
  senderNames,
  deliveredSeq = 0,
  readSeq = 0,
  highlightedSeq,
}: MessageListProps) {
  const endRef = useRef<HTMLDivElement>(null);
  const mountedRef = useRef(false);

  useEffect(() => {
    // Jump instantly on first paint, then animate as new messages arrive.
    endRef.current?.scrollIntoView({
      behavior: mountedRef.current ? "smooth" : "auto",
    });
    mountedRef.current = true;
  }, [messages.length, typingName]);

  useEffect(() => {
    if (highlightedSeq == null) return;
    const el = document.getElementById(`msg-seq-${highlightedSeq}`);
    el?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [highlightedSeq]);

  const rows: ReactNode[] = [];
  let lastDay = "";
  let prevSenderId = "";
  for (const m of messages) {
    const day = formatDateDivider(m.createdAt);
    const dayChanged = day !== lastDay;
    if (dayChanged) {
      rows.push(<DateDivider key={`day-${m.id}`} label={day} />);
      lastDay = day;
    }
    const mine = m.senderId === currentUserId;
    const firstOfGroup = dayChanged || m.senderId !== prevSenderId;
    prevSenderId = m.senderId;
    const status = mine
      ? m.sequenceNumber <= readSeq
        ? "SEEN"
        : m.sequenceNumber <= deliveredSeq
          ? "DELIVERED"
          : "SENT"
      : undefined;
    const highlighted = m.sequenceNumber === highlightedSeq;
    const onMediaClick = m.mediaId
      ? () => {
          getMediaUrl(m.mediaId!).then((r) => window.open(r.url, "_blank")).catch(() => {
            if (m.thumbnailUrl) window.open(m.thumbnailUrl, "_blank");
          });
        }
      : undefined;
    rows.push(
      <div
        key={m.id}
        id={`msg-seq-${m.sequenceNumber}`}
        className={highlighted ? "rounded-lg outline outline-2 outline-primary/40 transition-all duration-700" : undefined}
      >
        <MessageBubble
          content={m.content}
          createdAt={m.createdAt}
          mine={mine}
          status={status}
          firstOfGroup={firstOfGroup}
          senderLabel={
            !mine && firstOfGroup ? senderNames?.[m.senderId] : undefined
          }
          senderName={
            !mine && firstOfGroup ? senderNames?.[m.senderId] : undefined
          }
          messageType={m.type}
          thumbnailUrl={m.thumbnailUrl}
          mediaId={m.mediaId}
          mediaType={m.mediaType}
          originalFilename={m.originalFilename}
          onMediaClick={onMediaClick}
        />
      </div>,
    );
  }

  return (
    <div className="flex-1 overflow-y-auto bg-surface-bright px-6 py-4">
      {messages.length === 0 && !typingName && (
        <div className="flex h-full items-center justify-center text-sm text-on-surface-variant">
          No messages yet — say hello
        </div>
      )}
      {rows}
      {typingName && <TypingDots name={typingName} />}
      <div ref={endRef} />
    </div>
  );
}
