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
        "flex h-9 w-9 items-center justify-center rounded-full text-gray-500 transition hover:bg-gray-100 hover:text-gray-700 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none active:scale-95",
        className,
      )}
      {...rest}
    >
      {children}
    </button>
  );
}
