import type { ReactNode } from "react";
import SearchBar from "./SearchBar";

interface ListPanelProps {
  title: string;
  actions?: ReactNode;
  searchPlaceholder?: string;
  children: ReactNode;
}

/** Middle pane shell: title header, search, and a scrollable list body. */
export default function ListPanel({
  title,
  actions,
  searchPlaceholder,
  children,
}: ListPanelProps) {
  return (
    <aside className="flex w-80 shrink-0 flex-col border-r border-gray-200 bg-white">
      <header className="flex items-center justify-between px-4 pt-4 pb-2">
        <h1 className="text-xl font-bold text-gray-900">{title}</h1>
        <div className="flex items-center gap-1 text-gray-500">{actions}</div>
      </header>

      <div className="px-3 pb-2">
        <SearchBar placeholder={searchPlaceholder} />
      </div>

      <div className="flex-1 overflow-y-auto">{children}</div>
    </aside>
  );
}
