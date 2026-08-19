import { cn } from "../../lib/utils/cn";

export function Skeleton({ className }: { className?: string }) {
  return (
    <div className={cn("animate-pulse rounded-md bg-surface-container-high", className)} />
  );
}

export function ConversationRowSkeleton() {
  return (
    <div className="flex items-center gap-3 border-b border-outline-variant p-4">
      <Skeleton className="h-12 w-12 rounded-full" />
      <div className="min-w-0 flex-1 space-y-2">
        <Skeleton className="h-3.5 w-28" />
        <Skeleton className="h-3 w-40" />
      </div>
    </div>
  );
}

export function ConversationListSkeleton({ rows = 6 }: { rows?: number }) {
  return (
    <div>
      {Array.from({ length: rows }, (_, i) => (
        <ConversationRowSkeleton key={i} />
      ))}
    </div>
  );
}

export function MessageListSkeleton() {
  const widths = ["w-40", "w-56", "w-32", "w-48", "w-44", "w-36"];
  return (
    <div className="flex-1 space-y-3 overflow-hidden bg-surface-bright px-6 py-6">
      {widths.map((w, i) => (
        <div
          key={i}
          className={cn("flex", i % 2 === 0 ? "justify-start" : "justify-end")}
        >
          <Skeleton className={cn("h-10 rounded-lg", w)} />
        </div>
      ))}
    </div>
  );
}
