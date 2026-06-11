import { FiCheck } from "react-icons/fi";
import { cn } from "../../lib/utils/cn";
import { formatMessageTime } from "../../lib/utils/time";

/** Delivery state for an own message, derived from receipts (set in the realtime stage). */
export type MessageStatus = "SENT" | "DELIVERED" | "SEEN";

interface MessageBubbleProps {
  content: string;
  createdAt: string;
  mine: boolean;
  status?: MessageStatus;
  /** Sender label, shown for others' messages in groups. */
  senderLabel?: string;
  /** First bubble in a same-sender run — gets extra top spacing. */
  firstOfGroup?: boolean;
}

function Ticks({ status }: { status: MessageStatus }) {
  if (status === "SENT") return <FiCheck className="text-[12px]" />;
  return (
    <span className={cn("flex", status === "SEEN" && "text-sky-300")}>
      <FiCheck className="-mr-[6px] text-[12px]" />
      <FiCheck className="text-[12px]" />
    </span>
  );
}

export default function MessageBubble({
  content,
  createdAt,
  mine,
  status,
  senderLabel,
  firstOfGroup = true,
}: MessageBubbleProps) {
  return (
    <div
      className={cn(
        "animate-msg-in flex",
        firstOfGroup ? "mt-2" : "mt-0.5",
        mine ? "justify-end" : "justify-start",
      )}
    >
      <div
        className={cn(
          "max-w-[70%] rounded-2xl px-4 py-2 text-sm",
          mine
            ? "bg-brand rounded-br-md text-white"
            : "rounded-bl-md bg-white text-gray-800 shadow-sm",
        )}
      >
        {!mine && senderLabel && (
          <p className="mb-0.5 text-xs font-semibold text-blue-600">
            {senderLabel}
          </p>
        )}
        <p className="break-words whitespace-pre-wrap">{content}</p>
        <div
          className={cn(
            "mt-1 flex items-center justify-end gap-1 text-[10px]",
            mine ? "text-white/70" : "text-gray-400",
          )}
        >
          <span>{formatMessageTime(createdAt)}</span>
          {mine && status && <Ticks status={status} />}
        </div>
      </div>
    </div>
  );
}
