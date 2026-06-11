import { cn } from "../../lib/utils/cn";

interface AvatarProps {
  name: string;
  size?: number;
  className?: string;
}

/**
 * Brand-adjacent gradient palette. The default brand gradient stays first; the
 * rest are nearby hues so a list of avatars is scannable without going off-brand.
 * Literal class strings so Tailwind's scanner keeps them.
 */
const PALETTE = [
  "from-sky-500 to-blue-600",
  "from-blue-500 to-indigo-600",
  "from-cyan-500 to-sky-600",
  "from-indigo-500 to-blue-600",
  "from-sky-400 to-cyan-600",
  "from-blue-400 to-sky-600",
] as const;

/** Stable index from a name so the same person always gets the same color. */
function colorFor(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) | 0;
  }
  return PALETTE[Math.abs(hash) % PALETTE.length];
}

/** Circular gradient avatar showing the first initial. */
export default function Avatar({ name, size = 40, className }: AvatarProps) {
  const initial = name.trim().charAt(0).toUpperCase() || "?";
  return (
    <div
      className={cn(
        "flex shrink-0 items-center justify-center rounded-full bg-gradient-to-br font-semibold text-white select-none",
        colorFor(name),
        className,
      )}
      style={{ width: size, height: size, fontSize: size * 0.4 }}
    >
      {initial}
    </div>
  );
}
