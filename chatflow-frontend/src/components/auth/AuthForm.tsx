import { Link } from "react-router-dom";
import { useForm } from "react-hook-form";

export interface AuthFormData {
  username: string;
  password: string;
}

interface AuthFormProps {
  title: string;
  submitText: string;
  footerText: string;
  footerLinkText: string;
  footerLinkTo: string;
  isLoading?: boolean;
  /** Server-side error message to display (e.g. invalid credentials). */
  error?: string | null;
  onSubmit: (data: AuthFormData) => void | Promise<void>;
}

export default function AuthForm({
  title,
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
  } = useForm<AuthFormData>({
    defaultValues: {
      username: "",
      password: "",
    },
  });

  return (
    <div className="w-full max-w-md">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        <div className="space-y-2">
          <h1 className="text-3xl font-bold">{title}</h1>

          <p className="text-sm text-gray-500">Sign in to continue chatting</p>
        </div>

        {error && (
          <div
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"
          >
            {error}
          </div>
        )}

        <div className="space-y-1">
          <label htmlFor="username" className="text-sm font-medium">
            Username
          </label>

          <input
            id="username"
            type="text"
            placeholder="Enter username"
            className="w-full rounded-lg border border-gray-300 px-4 py-3 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/40"
            {...register("username", {
              required: "Username is required",
              minLength: {
                value: 3,
                message: "Username must be at least 3 characters",
              },
            })}
          />

          {errors.username && (
            <p className="text-sm text-red-500">{errors.username.message}</p>
          )}
        </div>

        <div className="space-y-1">
          <label htmlFor="password" className="text-sm font-medium">
            Password
          </label>

          <input
            id="password"
            type="password"
            placeholder="Enter password"
            className="w-full rounded-lg border border-gray-300 px-4 py-3 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/40"
            {...register("password", {
              required: "Password is required",
              minLength: {
                value: 6,
                message: "Password must be at least 6 characters",
              },
            })}
          />

          {errors.password && (
            <p className="text-sm text-red-500">{errors.password.message}</p>
          )}
        </div>

        <button
          type="submit"
          disabled={isLoading}
          className="bg-brand w-full rounded-lg px-4 py-3 font-semibold text-white transition hover:opacity-95 active:scale-[.99] disabled:opacity-50"
        >
          {isLoading ? "Loading..." : submitText}
        </button>

        <p className="text-center text-sm text-gray-500">
          {footerText}{" "}
          <Link
            to={footerLinkTo}
            className="font-semibold text-blue-600 hover:underline"
          >
            {footerLinkText}
          </Link>
        </p>
      </form>
    </div>
  );
}
