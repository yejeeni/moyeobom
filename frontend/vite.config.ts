import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 개발 서버에서 /api와 /ws를 백엔드(8080)로 넘겨 같은 출처처럼 쓴다
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
