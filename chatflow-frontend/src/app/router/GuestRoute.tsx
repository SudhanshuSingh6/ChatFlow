import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../provider/AuthProvider";

/** Gate for guest-only routes (login/register). Sends already-authenticated
 *  users to the app instead of showing the auth screens. */
export default function GuestRoute() {
  const { isAuthenticated } = useAuth();

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
