import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the browser talks only to Vite (port 5173). Vite forwards /api to the
// backend, so the browser sees one origin and no CORS setup is needed for the API.
// File uploads go straight to MinIO through presigned URLs, as in production.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.BACKEND_URL || 'http://localhost:8080',
    },
  },
});
