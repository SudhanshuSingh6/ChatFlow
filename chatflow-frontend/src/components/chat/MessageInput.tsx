import { useRef, useState, type KeyboardEvent } from "react";
import { FiPlusCircle, FiSmile, FiSend, FiLoader } from "react-icons/fi";
import { uploadMedia } from "../../lib/api/media";

interface MessageInputProps {
  onSend: (text: string) => void;
  placeholder?: string;
  disabled?: boolean;
  onTyping?: (typing: boolean) => void;
  conversationId?: string;
}

const TYPING_IDLE_MS = 2500;

export default function MessageInput({
  onSend,
  placeholder = "Type a message...",
  disabled = false,
  onTyping,
  conversationId,
}: MessageInputProps) {
  const [text, setText] = useState("");
  const typingRef = useRef(false);
  const idleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [uploadError, setUploadError] = useState(false);

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

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  };

  const onFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !conversationId) return;
    e.target.value = "";
    setUploadError(false);
    setUploadProgress(0);
    try {
      await uploadMedia(conversationId, file, setUploadProgress);
    } catch {
      setUploadError(true);
      setTimeout(() => setUploadError(false), 3000);
    } finally {
      setUploadProgress(null);
    }
  };

  const uploading = uploadProgress !== null;

  return (
    <div className="border-t border-outline-variant bg-surface-container-lowest p-4">
      {/* Upload progress bar */}
      {uploading && (
        <div className="mb-2 h-1 overflow-hidden rounded-full bg-surface-container">
          <div
            className="h-full rounded-full bg-primary transition-all duration-200"
            style={{ width: `${uploadProgress}%` }}
          />
        </div>
      )}
      {uploadError && (
        <p className="mb-2 text-xs text-error">Upload failed — please try again.</p>
      )}

      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*,video/*,application/pdf,.doc,.docx,.txt"
        className="hidden"
        onChange={onFileChange}
      />

      <div className="flex items-end gap-2 rounded-xl border border-outline-variant bg-surface p-2 transition-all focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/20">
        <button
          type="button"
          aria-label="Attach"
          disabled={disabled || uploading}
          onClick={() => fileInputRef.current?.click()}
          className="p-2 text-on-surface-variant transition-colors rounded-full hover:bg-surface-container hover:text-primary disabled:opacity-40"
        >
          {uploading ? (
            <FiLoader className="text-xl animate-spin text-primary" />
          ) : (
            <FiPlusCircle className="text-xl" />
          )}
        </button>

        <button
          type="button"
          aria-label="Emoji"
          disabled={disabled}
          className="p-2 text-on-surface-variant transition-colors rounded-full hover:bg-surface-container hover:text-primary disabled:opacity-40"
        >
          <FiSmile className="text-xl" />
        </button>

        <textarea
          rows={1}
          value={text}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={disabled ? "Connecting…" : placeholder}
          disabled={disabled}
          className="flex-1 resize-none bg-transparent py-2 text-sm text-on-surface placeholder:text-on-surface-variant outline-none disabled:opacity-60"
          style={{ minHeight: 40, maxHeight: 128 }}
        />

        <button
          type="button"
          onClick={submit}
          disabled={disabled || !text.trim()}
          aria-label="Send"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary text-on-primary shadow-sm transition-all hover:bg-primary-container active:scale-95 disabled:opacity-40"
        >
          <FiSend />
        </button>
      </div>
    </div>
  );
}
