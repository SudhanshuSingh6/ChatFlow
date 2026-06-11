import { cn } from "../../lib/utils/cn";

/** Base shimmer block. Compose into list/message placeholders. */
export function Skeleton({ className }: { className?: string }) {
  return (
    <div className={cn("animate-pulse rounded-md bg-gray-200", className)} />
  );
}

/** Placeholder mirroring a ConversationRow's layout. */
export function ConversationRowSkeleton() {
  return (
    <div className="flex items-center gap-3 px-3 py-3">
      <Skeleton className="h-10 w-10 rounded-full" />
      <div className="min-w-0 flex-1 space-y-2">
        <Skeleton className="h-3.5 w-28" />
        <Skeleton className="h-3 w-40" />
      </div>
    </div>
  );
}

/** A list of conversation-row placeholders for the middle pane. */
export function ConversationListSkeleton({ rows = 6 }: { rows?: number }) {
  return (
    <div>
      {Array.from({ length: rows }, (_, i) => (
        <ConversationRowSkeleton key={i} />
      ))}
    </div>
  );
}

/** Placeholder for the conversation pane while history loads. */
export function MessageListSkeleton() {
  const widths = ["w-40", "w-56", "w-32", "w-48", "w-44", "w-36"];
  return (
    <div className="flex-1 space-y-2 overflow-hidden bg-slate-50 px-5 py-4">
      {widths.map((w, i) => (
        <div
          key={i}
          className={cn("flex", i % 2 === 0 ? "justify-start" : "justify-end")}
        >
          <Skeleton className={cn("h-9 rounded-2xl", w)} />
        </div>
      ))}
    </div>
  );
}
