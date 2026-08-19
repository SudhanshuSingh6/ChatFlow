import { useNavigate } from "react-router-dom";
import AuthForm, { type AuthFormData } from "../../components/auth/AuthForm";
import AuthLayout from "../../components/auth/AuthLayout";
import { useAuth } from "../../app/provider/AuthProvider";

export default function RegisterPage() {
  const { register, isPending, error } = useAuth();
  const navigate = useNavigate();

  const handleRegister = async (data: AuthFormData) => {
    try {
      // Backend logs the user in on register (returns a token), so go home.
      await register(data);
      navigate("/", { replace: true });
    } catch {
      // Error message is surfaced via `error` from useAuth().
    }
  };

  return (
    <AuthLayout>
      <AuthForm
        title="Create Account"
        subtitle="Join ChatFlow and start chatting"
        submitText="Register"
        footerText="Already have an account?"
        footerLinkText="Login"
        footerLinkTo="/login"
        isLoading={isPending}
        error={error}
        onSubmit={handleRegister}
      />
    </AuthLayout>
  );
}
