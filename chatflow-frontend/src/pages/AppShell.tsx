import { Outlet } from "react-router-dom";
import Sidebar from "../components/nav/Sidebar";

/** Authenticated layout: sidebar + active list/conversation panes. */
export default function AppShell() {
  return (
    <div className="flex h-screen w-screen overflow-hidden bg-background">
      <Sidebar />
      <Outlet />
    </div>
  );
}
