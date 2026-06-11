import { useRef, useState, type KeyboardEvent } from "react";
import { FiPlus, FiSend } from "react-icons/fi";

interface MessageInputProps {
  onSend: (text: string) => void;
  placeholder?: string;
  /** When true the input is read-only (e.g. realtime not connected yet). */
  disabled?: boolean;
  /** Emitted true while composing, false on idle/submit (for TYPING frames). */
  onTyping?: (typing: boolean) => void;
}

const TYPING_IDLE_MS = 2500;

export default function MessageInput({
  onSend,
  placeholder = "Type a message…",
  disabled = false,
  onTyping,
}: MessageInputProps) {
  const [text, setText] = useState("");
  const typingRef = useRef(false);
  const idleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const stopTyping = () => {
    if (idleTimer.current) clearTimeout(idleTimer.current);
    idleTimer.current = null;
    if (typingRef.current) {
      typingRef.current = false;
      onTyping?.(false);
    }
  };

  const onChange = (value: string) => {
    setText(value);
    if (disabled || !onTyping) return;
    if (!typingRef.current && value.trim()) {
      typingRef.current = true;
      onTyping(true);
    }
    if (idleTimer.current) clearTimeout(idleTimer.current);
    idleTimer.current = setTimeout(stopTyping, TYPING_IDLE_MS);
  };

  const submit = () => {
    if (disabled) return;
    const trimmed = text.trim();
    if (!trimmed) return;
    onSend(trimmed);
    setText("");
    stopTyping();
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  };

  return (
    <div className="flex items-center gap-2 border-t border-gray-200 bg-white px-4 py-3">
      <button
        type="button"
        aria-label="Attach"
        disabled={disabled}
        className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-gray-400 transition hover:bg-gray-100 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none active:scale-95 disabled:opacity-40"
      >
        <FiPlus className="text-xl" />
      </button>

      <input
        value={text}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={onKeyDown}
        placeholder={disabled ? "Connecting…" : placeholder}
        disabled={disabled}
        className="flex-1 rounded-full bg-gray-100 px-4 py-2.5 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-60"
      />

      <button
        type="button"
        onClick={submit}
        disabled={disabled || !text.trim()}
        aria-label="Send"
        className="bg-brand flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-white transition active:scale-95 disabled:opacity-40"
      >
        <FiSend />
      </button>
    </div>
  );
}
