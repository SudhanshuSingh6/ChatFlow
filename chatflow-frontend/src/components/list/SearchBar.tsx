import { FiSearch } from "react-icons/fi";

export default function SearchBar({
  placeholder = "Search",
}: {
  placeholder?: string;
}) {
  return (
    <div className="flex items-center gap-2 rounded-lg bg-gray-100 px-3 py-2 text-sm text-gray-500">
      <FiSearch className="shrink-0" />
      <input
        className="w-full bg-transparent text-gray-700 outline-none placeholder:text-gray-400"
        placeholder={placeholder}
      />
    </div>
  );
}
