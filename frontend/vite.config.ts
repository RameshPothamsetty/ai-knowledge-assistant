import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Builds straight into the Spring Boot static resources so the whole app still
// ships as a single jar. Set VITE_OUT_DIR when building inside Docker, where
// the frontend and backend live in separate stages.
const outDir = process.env.VITE_OUT_DIR ?? '../src/main/resources/static';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  base: '/',
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
  build: {
    outDir,
    emptyOutDir: true,
  },
});
