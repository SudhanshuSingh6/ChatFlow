import { FiMessageCircle, FiUsers, FiSmile, FiLogOut } from "react-icons/fi";
import RailButton from "./RailButton";
import { useAuth } from "../../app/provider/AuthProvider";

/** Far-left icon rail: logo · Chats/Groups/Friends · logout. */
export default function Rail() {
  const { logout } = useAuth();

  return (
    <nav className="bg-brand flex w-16 flex-col items-center gap-2 py-4">
      <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-white shadow-sm">
        <span className="material-symbols-outlined fill-1 text-primary text-2xl">forum</span>
      </div>

      <RailButton to="/chats" label="Chats" icon={<FiMessageCircle />} />
      <RailButton to="/groups" label="Groups" icon={<FiUsers />} />
      <RailButton to="/friends" label="Friends" icon={<FiSmile />} />

      <button
        type="button"
        onClick={logout}
        title="Logout"
        aria-label="Logout"
        className="mt-auto flex h-12 w-12 items-center justify-center rounded-xl text-white/70 transition hover:bg-white/15 hover:text-white focus-visible:ring-2 focus-visible:ring-white/80 focus-visible:outline-none active:scale-95"
      >
        <FiLogOut className="text-xl" />
      </button>
    </nav>
  );
}
