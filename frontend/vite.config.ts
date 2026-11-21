import { searchForWorkspaceRoot } from 'vite';
import { defineConfig } from 'vitest/config';

// Declaracion minima: evita sumar @types/node (fuera de la lista aprobada de dependencias).
declare const process: { env: Record<string, string | undefined>; cwd(): string };

const backendUrl = process.env['BACKEND_URL'] || 'http://localhost:8080';

export default defineConfig({
  server: {
    host: 'localhost',
    // design-system/ vive en la raiz del repo, fuera de la raiz de Vite (frontend/).
    fs: { allow: [searchForWorkspaceRoot(process.cwd()), '../design-system'] },
    proxy: {
      '/api': { target: backendUrl, changeOrigin: true },
      '/actuator': { target: backendUrl, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    restoreMocks: true,
    // Por defecto Vitest vacia los .css (tambien con ?raw); styles.test.ts necesita su texto real.
    css: { include: [/.+/] },
    // El patron por defecto excluye `vite.config.*`, lo que dejaria afuera a vite.config.test.ts.
    exclude: ['node_modules/**', 'dist/**'],
  },
});
