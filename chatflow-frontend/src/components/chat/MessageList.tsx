import { useEffect, useRef, type ReactNode } from "react";
import MessageBubble from "./MessageBubble";
import DateDivider from "./DateDivider";
import TypingDots from "./TypingDots";
import { formatDateDivider } from "../../lib/utils/time";
import type { Message } from "../../types/domain";

interface MessageListProps {
  messages: Message[];
  currentUserId: string;
  typingName?: string;
  /** Map senderId -> display name, for group sender labels. */
  senderNames?: Record<string, string>;
  /** Highest seq others have delivered/read — drives own-message ticks. */
  deliveredSeq?: number;
  readSeq?: number;
}

export default function MessageList({
  messages,
  currentUserId,
  typingName,
  senderNames,
  deliveredSeq = 0,
  readSeq = 0,
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
    rows.push(
      <MessageBubble
        key={m.id}
        content={m.content}
        createdAt={m.createdAt}
        mine={mine}
        status={status}
        firstOfGroup={firstOfGroup}
        senderLabel={
          !mine && firstOfGroup ? senderNames?.[m.senderId] : undefined
        }
      />,
    );
  }

  return (
    <div className="flex-1 overflow-y-auto bg-slate-50 px-5 py-4">
      {messages.length === 0 && !typingName && (
        <div className="flex h-full items-center justify-center text-sm text-gray-400">
          No messages yet — say hello 👋
        </div>
      )}
      {rows}
      {typingName && <TypingDots name={typingName} />}
      <div ref={endRef} />
    </div>
  );
}
