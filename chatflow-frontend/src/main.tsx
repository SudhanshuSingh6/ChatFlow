import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import QueryProvider from './app/provider/QueryProvider.tsx'
import AuthProvider from './app/provider/AuthProvider.tsx'
import WebSocketProvider from './app/provider/WebSocketProvider.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryProvider>
      <AuthProvider>
        <WebSocketProvider>
          <App />
        </WebSocketProvider>
      </AuthProvider>
    </QueryProvider>
  </StrictMode>,
)
