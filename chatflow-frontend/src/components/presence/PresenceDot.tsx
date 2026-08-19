import { cn } from "../../lib/utils/cn";

export default function PresenceDot({
  online,
  className,
}: {
  online: boolean;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-block h-3 w-3 rounded-full border-2 border-surface-container-lowest",
        online ? "bg-tertiary-container" : "bg-outline-variant",
        className,
      )}
    />
  );
}
