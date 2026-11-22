// @vitest-environment node
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { UserConfig } from 'vite';

async function loadConfig(): Promise<UserConfig> {
  vi.resetModules();
  const module = (await import('./vite.config')) as { default: UserConfig };
  return module.default;
}

function targetOf(config: UserConfig, path: string): unknown {
  const entry = config.server?.proxy?.[path];
  return typeof entry === 'string' ? entry : entry?.target;
}

describe('vite proxy', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('reenvia /api y /actuator a http://localhost:8080 por defecto', async () => {
    vi.stubEnv('BACKEND_URL', '');

    const config = await loadConfig();

    expect(targetOf(config, '/api')).toBe('http://localhost:8080');
    expect(targetOf(config, '/actuator')).toBe('http://localhost:8080');
  });

  it('usa BACKEND_URL cuando esta definida', async () => {
    vi.stubEnv('BACKEND_URL', 'http://localhost:9090');

    const config = await loadConfig();

    expect(targetOf(config, '/api')).toBe('http://localhost:9090');
    expect(targetOf(config, '/actuator')).toBe('http://localhost:9090');
  });
});

describe('vite server', () => {
  it('escucha solo en localhost, no en todas las interfaces', async () => {
    const config = await loadConfig();

    expect(config.server?.host).toBe('localhost');
  });

  it('permite servir design-system (tokens, brand.js, logo) fuera de la raiz de Vite', async () => {
    const config = await loadConfig();

    expect(config.server?.fs?.allow).toContain('../design-system');
  });
});
