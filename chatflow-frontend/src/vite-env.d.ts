/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the ChatFlow backend (REST + WebSocket). */
  readonly VITE_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
