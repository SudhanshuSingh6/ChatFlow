import { NavLink } from "react-router-dom";
import type { ReactNode } from "react";
import { cn } from "../../lib/utils/cn";

interface RailButtonProps {
  to: string;
  label: string;
  icon: ReactNode;
}

export default function RailButton({ to, label, icon }: RailButtonProps) {
  return (
    <NavLink
      to={to}
      title={label}
      aria-label={label}
      className={({ isActive }) =>
        cn(
          "flex h-12 w-12 items-center justify-center rounded-xl text-xl transition focus-visible:ring-2 focus-visible:ring-white/80 focus-visible:outline-none active:scale-95",
          isActive
            ? "bg-white text-blue-600 shadow-md"
            : "text-white/70 hover:bg-white/15 hover:text-white",
        )
      }
    >
      {icon}
    </NavLink>
  );
}
