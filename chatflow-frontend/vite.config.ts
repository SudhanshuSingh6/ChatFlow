import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  // The gateway (8088) is the public entry point; it routes /api -> core and
  // /ai -> ai. WebSockets hit the realtime service (8083) directly. Proxying
  // through the dev server keeps requests same-origin, so there's no CORS.
  const gateway = env.VITE_GATEWAY_URL ?? "http://localhost:8088";
  const realtime = env.VITE_WS_URL ?? "ws://localhost:8083";

  return {
    plugins: [react(), tailwindcss()],
    server: {
      proxy: {
        "/api": { target: gateway, changeOrigin: true },
        "/ai": { target: gateway, changeOrigin: true },
        "/ws": { target: realtime, ws: true, changeOrigin: true },
      },
    },
  };
});
