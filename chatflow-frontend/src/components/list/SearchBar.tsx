import { FiSearch } from "react-icons/fi";

export default function SearchBar({
  placeholder = "Search",
}: {
  placeholder?: string;
}) {
  return (
    <div className="relative">
      <FiSearch className="absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant" />
      <input
        className="w-full rounded-lg border border-outline-variant bg-surface py-2 pl-9 pr-4 text-sm text-on-surface placeholder:text-on-surface-variant outline-none transition-all focus:border-primary focus:ring-2 focus:ring-primary/20"
        placeholder={placeholder}
      />
    </div>
  );
}
