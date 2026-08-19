import type { ReactNode } from "react";
import SearchBar from "./SearchBar";

interface ListPanelProps {
  title: string;
  actions?: ReactNode;
  searchPlaceholder?: string;
  children: ReactNode;
}

/** Middle pane: title header, search, scrollable list body. */
export default function ListPanel({
  title,
  actions,
  searchPlaceholder,
  children,
}: ListPanelProps) {
  return (
    <aside className="flex w-80 shrink-0 flex-col border-r border-outline-variant bg-surface-container-lowest">
      <div className="border-b border-outline-variant p-4">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-xl font-bold text-on-surface">{title}</h2>
          {actions && (
            <div className="flex items-center gap-1 text-on-surface-variant">
              {actions}
            </div>
          )}
        </div>
        <SearchBar placeholder={searchPlaceholder} />
      </div>

      <div className="flex-1 overflow-y-auto">{children}</div>
    </aside>
  );
}
