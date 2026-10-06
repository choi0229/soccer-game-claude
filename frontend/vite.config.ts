import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 개발 서버는 /api 를 백엔드로 넘긴다 (docker 에서는 nginx 가 같은 일을 한다)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.API_URL ?? 'http://localhost:8080',
    },
  },
});
