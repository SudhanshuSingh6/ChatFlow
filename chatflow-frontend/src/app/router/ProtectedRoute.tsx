import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../provider/AuthProvider";

/** Gate for authenticated-only routes. Bounces guests to /login, remembering
 *  where they were headed so login can send them back. */
export default function ProtectedRoute() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
