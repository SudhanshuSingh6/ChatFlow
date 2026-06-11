import { FiMessageSquare } from "react-icons/fi";

/** Default right-pane content when nothing is selected. */
export default function EmptyState({
  message = "Select a conversation to start chatting",
}: {
  message?: string;
}) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 bg-slate-50 px-6 text-center text-gray-400">
      <div className="bg-brand flex h-16 w-16 items-center justify-center rounded-2xl text-3xl text-white">
        <FiMessageSquare />
      </div>
      <p className="text-lg font-semibold text-gray-500">ChatFlow</p>
      <p className="max-w-xs text-sm">{message}</p>
    </div>
  );
}
