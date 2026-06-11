/** Animated "typing…" indicator. */
export default function TypingDots({ name }: { name?: string }) {
  return (
    <div className="flex items-center gap-2 px-1 py-1 text-xs text-gray-400">
      <span className="flex gap-1">
        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400 [animation-delay:-0.3s]" />
        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400 [animation-delay:-0.15s]" />
        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400" />
      </span>
      {name && <span>{name} is typing…</span>}
    </div>
  );
}
