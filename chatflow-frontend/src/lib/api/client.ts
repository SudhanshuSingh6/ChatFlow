import axios from "axios";
import { getAuthToken, clearAuth } from "../../store/authStore";

// Empty by default: requests are same-origin and the Vite dev proxy (see
// vite.config.ts) forwards /api to the gateway, avoiding CORS. Set VITE_API_URL
// to hit a backend directly (e.g. against a deployed gateway).
export const API_BASE_URL = import.meta.env.VITE_API_URL ?? "";

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
});

// Attach the JWT to every request.
apiClient.interceptors.request.use((config) => {
  const token = getAuthToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// A 401 on a non-auth endpoint means the session expired — drop it so the app
// falls back to the login screen. We skip /api/auth/* so a failed login (which
// also returns 401 for bad credentials) doesn't trigger a spurious "logout".
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const url: string = error.config?.url ?? "";
    if (error.response?.status === 401 && !url.includes("/api/auth/")) {
      clearAuth();
    }
    return Promise.reject(error);
  },
);

/** Pull a human-readable message out of an axios error (backend uses RFC-7807 ProblemDetail). */
export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as
      | { detail?: string; message?: string }
      | undefined;
    return data?.detail ?? data?.message ?? error.message;
  }
  return error instanceof Error ? error.message : "Something went wrong";
}
