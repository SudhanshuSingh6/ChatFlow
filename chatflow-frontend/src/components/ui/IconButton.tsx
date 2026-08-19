import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "../../lib/utils/cn";

interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
}

export default function IconButton({
  children,
  className,
  ...rest
}: IconButtonProps) {
  return (
    <button
      type="button"
      className={cn(
        "flex h-9 w-9 items-center justify-center rounded-full text-on-surface-variant transition-colors",
        "hover:bg-surface-container hover:text-on-surface",
        "focus-visible:ring-2 focus-visible:ring-primary/40 focus-visible:outline-none",
        "active:scale-95",
        className,
      )}
      {...rest}
    >
      {children}
    </button>
  );
}
