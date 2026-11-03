import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const localApiAuthToken = process.env.UEP_LOCAL_AUTH_TOKEN
const localApiProxyHeaders: Record<string, string> = localApiAuthToken
  ? { 'X-UEP-Local-Auth': localApiAuthToken }
  : {}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false,
        headers: localApiProxyHeaders,
      },
    },
  },
})