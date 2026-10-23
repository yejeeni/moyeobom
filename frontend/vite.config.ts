import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 개발 서버에서 /api와 /ws를 백엔드로 넘겨 같은 출처처럼 쓴다. 백엔드 주소는 MOYEOBOM_BACKEND로 바꿀 수 있다
const backend = process.env.MOYEOBOM_BACKEND ?? 'localhost:8080'

// 빌드마다 바뀌는 화면 버전. 열려 있는 화면이 서버의 version.json과 비교해 새로 배포됐는지 안다
const appVersion = new Date().toISOString()

export default defineConfig({
  plugins: [
    react(),
    {
      name: 'version-file',
      apply: 'build',
      generateBundle() {
        this.emitFile({ type: 'asset', fileName: 'version.json', source: JSON.stringify({ version: appVersion }) })
      },
    },
  ],
  define: {
    __APP_VERSION__: JSON.stringify(appVersion),
  },
  server: {
    port: 5173,
    proxy: {
      '/api': `http://${backend}`,
      '/ws': { target: `ws://${backend}`, ws: true },
    },
  },
})
