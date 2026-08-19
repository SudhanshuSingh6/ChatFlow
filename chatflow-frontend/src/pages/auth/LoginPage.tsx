import { useNavigate, useLocation } from "react-router-dom";
import AuthForm, { type AuthFormData } from "../../components/auth/AuthForm";
import AuthLayout from "../../components/auth/AuthLayout";
import { useAuth } from "../../app/provider/AuthProvider";

export default function LoginPage() {
  const { login, isPending, error } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // Return the user to wherever a ProtectedRoute bounced them from, else home.
  const from =
    (location.state as { from?: { pathname: string } } | null)?.from
      ?.pathname ?? "/";

  const handleLogin = async (data: AuthFormData) => {
    try {
      await login(data);
      navigate(from, { replace: true });
    } catch {
      // Error message is surfaced via `error` from useAuth().
    }
  };

  return (
    <AuthLayout>
      <AuthForm
        title="Welcome Back"
        subtitle="Sign in to continue chatting"
        submitText="Login"
        footerText="Don't have an account?"
        footerLinkText="Register"
        footerLinkTo="/register"
        isLoading={isPending}
        error={error}
        onSubmit={handleLogin}
      />
    </AuthLayout>
  );
}
