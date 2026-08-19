export default function TypingDots({ name }: { name?: string }) {
  return (
    <div className="mt-4 flex items-center gap-3 pl-11">
      <div className="flex items-center gap-1.5 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 shadow-sm">
        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-on-surface-variant [animation-delay:-0.3s]" />
        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-on-surface-variant [animation-delay:-0.15s]" />
        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-on-surface-variant" />
      </div>
      {name && (
        <span className="text-xs text-on-surface-variant">{name} is typing…</span>
      )}
    </div>
  );
}
