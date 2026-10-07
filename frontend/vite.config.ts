import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 개발 서버에서 /api와 /ws를 백엔드로 넘겨 같은 출처처럼 쓴다. 백엔드 주소는 MOYEOBOM_BACKEND로 바꿀 수 있다
const backend = process.env.MOYEOBOM_BACKEND ?? 'localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': `http://${backend}`,
      '/ws': { target: `ws://${backend}`, ws: true },
    },
  },
})
