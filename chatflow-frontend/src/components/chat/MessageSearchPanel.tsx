import { useMessageSearch } from "../../hooks/useMessageSearch";
import { formatMessageTime } from "../../lib/utils/time";
import type { Message } from "../../types/domain";

interface Props {
  conversationId: string;
  query: string;
  senderNames: Record<string, string>;
  currentUserId: string;
  onSelect: (seq: number) => void;
}

function highlight(text: string, query: string): string {
  if (!query) return text;
  const idx = text.toLowerCase().indexOf(query.toLowerCase());
  if (idx === -1) return text;
  return (
    text.slice(0, idx) +
    "[[" +
    text.slice(idx, idx + query.length) +
    "]]" +
    text.slice(idx + query.length)
  );
}

function HighlightedText({ text, query }: { text: string; query: string }) {
  const marked = highlight(text, query);
  const parts = marked.split(/\[\[|\]\]/);
  return (
    <span>
      {parts.map((part, i) =>
        i % 2 === 1 ? (
          <mark key={i} className="rounded bg-primary/20 px-0.5 text-primary not-italic">
            {part}
          </mark>
        ) : (
          <span key={i}>{part}</span>
        ),
      )}
    </span>
  );
}

function ResultRow({
  message,
  query,
  senderNames,
  currentUserId,
  onSelect,
}: {
  message: Message;
  query: string;
  senderNames: Record<string, string>;
  currentUserId: string;
  onSelect: (seq: number) => void;
}) {
  const mine = message.senderId === currentUserId;
  const senderLabel = mine ? "You" : (senderNames[message.senderId] ?? "Unknown");

  return (
    <button
      type="button"
      onClick={() => onSelect(message.sequenceNumber)}
      className="flex w-full flex-col gap-0.5 rounded-lg px-4 py-3 text-left transition-colors hover:bg-surface-container-low"
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-semibold text-on-surface">{senderLabel}</span>
        <span className="shrink-0 text-[10px] text-on-surface-variant">
          {formatMessageTime(message.createdAt)}
        </span>
      </div>
      <p className="line-clamp-2 text-sm text-on-surface-variant">
        <HighlightedText text={message.content} query={query} />
      </p>
    </button>
  );
}

export default function MessageSearchPanel({
  conversationId,
  query,
  senderNames,
  currentUserId,
  onSelect,
}: Props) {
  const { data: results, isLoading, isFetching } = useMessageSearch(conversationId, query);

  const isEmpty = query.trim().length < 2;

  return (
    <div className="absolute inset-x-0 top-16 z-30 max-h-[360px] overflow-y-auto border-b border-outline-variant bg-surface-container-lowest shadow-lg">
      {isEmpty && (
        <p className="px-4 py-3 text-sm text-on-surface-variant">
          Type at least 2 characters to search…
        </p>
      )}
      {!isEmpty && isLoading && (
        <p className="px-4 py-3 text-sm text-on-surface-variant">Searching…</p>
      )}
      {!isEmpty && !isLoading && results && results.length === 0 && (
        <p className="px-4 py-3 text-sm text-on-surface-variant">
          No messages match "{query}"
        </p>
      )}
      {results && results.length > 0 && (
        <div className="py-1">
          {!isFetching && (
            <p className="px-4 py-1 text-[10px] font-semibold uppercase tracking-wide text-on-surface-variant">
              {results.length} result{results.length !== 1 ? "s" : ""}
            </p>
          )}
          {results.map((msg) => (
            <ResultRow
              key={msg.id}
              message={msg}
              query={query}
              senderNames={senderNames}
              currentUserId={currentUserId}
              onSelect={onSelect}
            />
          ))}
        </div>
      )}
    </div>
  );
}
