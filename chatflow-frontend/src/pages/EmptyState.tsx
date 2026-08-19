export default function EmptyState({
  message = "Select a conversation to start chatting",
}: {
  message?: string;
}) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 bg-surface-bright px-6 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-primary text-on-primary shadow-sm">
        <span className="material-symbols-outlined fill-1 text-4xl">forum</span>
      </div>
      <p className="text-lg font-semibold text-on-surface">Chatflow</p>
      <p className="max-w-xs text-sm text-on-surface-variant">{message}</p>
    </div>
  );
}
