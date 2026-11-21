import { defineConfig } from 'vitest/config';

// Declaracion minima: evita sumar @types/node (fuera de la lista aprobada de dependencias).
declare const process: { env: Record<string, string | undefined> };

const backendUrl = process.env['BACKEND_URL'] || 'http://localhost:8080';

export default defineConfig({
  server: {
    host: 'localhost',
    proxy: {
      '/api': { target: backendUrl, changeOrigin: true },
      '/actuator': { target: backendUrl, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    restoreMocks: true,
    // El patron por defecto excluye `vite.config.*`, lo que dejaria afuera a vite.config.test.ts.
    exclude: ['node_modules/**', 'dist/**'],
  },
});
