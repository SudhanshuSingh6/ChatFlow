import { Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import Button from "../ui/Button";
import Input from "../ui/Input";

export interface AuthFormData {
  username: string;
  password: string;
}

interface AuthFormProps {
  title: string;
  subtitle: string;
  submitText: string;
  footerText: string;
  footerLinkText: string;
  footerLinkTo: string;
  isLoading?: boolean;
  error?: string | null;
  onSubmit: (data: AuthFormData) => void | Promise<void>;
}

export default function AuthForm({
  title,
  subtitle,
  submitText,
  footerText,
  footerLinkText,
  footerLinkTo,
  isLoading = false,
  error = null,
  onSubmit,
}: AuthFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AuthFormData>({ defaultValues: { username: "", password: "" } });

  return (
    <div className="w-full max-w-md rounded-2xl border border-outline-variant bg-surface-container-lowest p-8 shadow-sm">
      {/* Brand header */}
      <div className="mb-8 flex flex-col items-center gap-2">
        <span className="material-symbols-outlined fill-1 text-primary" style={{ fontSize: 48 }}>forum</span>
        <span className="text-lg font-bold tracking-tight text-on-surface">ChatFlow</span>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        {/* Title */}
        <div className="space-y-1 text-center">
          <h1 className="text-2xl font-bold text-on-surface">{title}</h1>
          <p className="text-sm text-on-surface-variant">{subtitle}</p>
        </div>

        {/* Server error */}
        {error && (
          <div
            role="alert"
            className="rounded-lg border border-error-container bg-error-container/30 px-4 py-3 text-sm text-error"
          >
            {error}
          </div>
        )}

        {/* Fields */}
        <div className="space-y-4">
          <Input
            id="username"
            label="Username"
            type="text"
            placeholder="Enter your username"
            error={errors.username?.message}
            required
            {...register("username", {
              required: "Username is required",
              minLength: { value: 3, message: "Username must be at least 3 characters" },
            })}
          />

          <Input
            id="password"
            label="Password"
            type="password"
            placeholder="Enter your password"
            error={errors.password?.message}
            required
            {...register("password", {
              required: "Password is required",
              minLength: { value: 6, message: "Password must be at least 6 characters" },
            })}
          />
        </div>

        <Button type="submit" fullWidth size="lg" isLoading={isLoading}>
          {submitText}
        </Button>

        <p className="text-center text-sm text-on-surface-variant">
          {footerText}{" "}
          <Link to={footerLinkTo} className="font-semibold text-primary hover:underline">
            {footerLinkText}
          </Link>
        </p>
      </form>
    </div>
  );
}
