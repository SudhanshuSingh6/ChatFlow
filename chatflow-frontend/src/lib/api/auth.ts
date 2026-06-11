import { apiClient } from "./client";

/** Mirrors the backend AuthResponse DTO. */
export interface AuthResponse {
  token: string;
  userId: string;
  username: string;
}

/** Mirrors the backend Login/RegisterRequest DTOs. */
export interface Credentials {
  username: string;
  password: string;
}

export async function loginRequest(creds: Credentials): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>("/api/auth/login", creds);
  return data;
}

export async function registerRequest(
  creds: Credentials,
): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>(
    "/api/auth/register",
    creds,
  );
  return data;
}
