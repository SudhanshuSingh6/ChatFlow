import { cn } from "../../lib/utils/cn";

/** Small status dot: green = online, gray = offline. */
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
        "inline-block h-2.5 w-2.5 rounded-full",
        online ? "bg-green-500" : "bg-gray-300",
        className,
      )}
    />
  );
}
