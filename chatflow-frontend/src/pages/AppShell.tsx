import { Outlet } from "react-router-dom";
import Rail from "../components/nav/Rail";

/** Authenticated layout: the icon rail plus the active list/conversation panes. */
export default function AppShell() {
  return (
    <div className="flex h-screen w-screen overflow-hidden bg-slate-50">
      <Rail />
      <Outlet />
    </div>
  );
}
