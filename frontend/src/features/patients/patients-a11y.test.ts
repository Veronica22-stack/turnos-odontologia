import { describe, expect, it, vi } from 'vitest';
import { createPatientTable, createPatientList } from './patient-row';
import { createPatientCard } from './patient-card';
import { createPatientForm } from './patient-form';
import type { PatientsGateway } from './patients-gateway';
import { createPatientsView } from './patients-view';
import type { PatientDetail, PatientSummary } from './patients-types';

const SUMMARY: PatientSummary = {
  id: 'pac-001',
  firstName: 'Julieta',
  lastName: 'Simulada',
  dni: '30.456.789',
  phone: '11 5555 0001',
  active: true,
};
const DETAIL: PatientDetail = {
  ...SUMMARY,
  email: 'julieta@ejemplo.invalid',
  birthDate: '1992-04-18',
  address: 'Calle Falsa 123',
  source: 'MANUAL',
  createdAt: '2025-03-12T12:41:00Z',
  updatedAt: '2026-11-18T17:07:00Z',
};

const SHEETS = import.meta.glob<string>('/src/styles/*.css', { query: '?raw', import: 'default', eager: true });
const BASE = Object.entries(SHEETS).find(([path]) => path.endsWith('/base.css'))?.[1] ?? '';
const PATIENTS = Object.entries(SHEETS).find(([path]) => path.endsWith('/patients.css'))?.[1] ?? '';
const TOKENS = Object.values(import.meta.glob<string>('../../../../design-system/tokens.css', { query: '?raw', import: 'default', eager: true }))[0] ?? '';

describe('pacientes - accesibilidad responsive (D8)', () => {
  it('9.2 tabla, lista y estados exponen las estructuras accesibles', async () => {
    const table = createPatientTable([SUMMARY], vi.fn());
    const list = createPatientList([SUMMARY], vi.fn());
    expect(table.querySelector('caption')?.classList.contains('sr-only')).toBe(true);
    expect([...table.querySelectorAll('thead th')].map((th) => th.getAttribute('scope'))).toEqual(['col', 'col', 'col']);
    expect(list.getAttribute('aria-label')).toBe('Pacientes activos');

    const gateway = {
      search: async () => ({ ok: true, data: [] }),
      get: async () => ({ ok: true, data: DETAIL }),
      update: async () => ({ ok: true, data: DETAIL }),
      deactivate: async () => ({ ok: true, data: undefined }),
    } as unknown as PatientsGateway;
    const view = createPatientsView({ gateway });
    expect(view.querySelector('[role="status"]')?.textContent).toContain('Cargando pacientes');
    expect(PATIENTS).toContain('.btn:focus-visible');
    expect(PATIENTS).toContain('var(--focus-ring)');

    const card = createPatientCard({ ...DETAIL, reviewRequired: true }, { onBack: vi.fn(), onEdit: vi.fn(), onDeactivate: vi.fn() });
    expect(card.querySelector('.badge-review')?.getAttribute('role')).toBe('status');
    const form = createPatientForm(
      { patient: DETAIL, draft: { firstName: '', lastName: '', phone: '', email: null, birthDate: null, address: null }, errors: { firstName: 'Error' }, hasUnknownField: true },
      { onSave: vi.fn(), onCancel: vi.fn() },
    );
    form.querySelector<HTMLButtonElement>('[data-action="save"]')?.click();
    expect(form.querySelector('#firstName-error')).not.toBeNull();
    await Promise.resolve();
  });

  it('9.2 el breakpoint desktop oculta la lista móvil y muestra la tabla con selectores propios', () => {
    expect(PATIENTS).toContain('.patients-view .table-wrap.only-desktop {\n  display: none;\n}');
    expect(PATIENTS).toMatch(/@media \(min-width: 64rem\)[\s\S]*?\.patients-view \.patient-list\.only-mobile\s*\{\s*display:\s*none/);
    expect(PATIENTS).toMatch(/@media \(min-width: 64rem\)[\s\S]*?\.patients-view \.table-wrap\.only-desktop\s*\{\s*display:\s*block/);
  });

  it('9.2 el anillo de foco y reduced motion vienen del design system sin literals nuevos', () => {
    expect(BASE).toContain('design-system/tokens.css');
    expect(TOKENS).toContain('--focus-ring:');
    expect(TOKENS).toContain('@media (prefers-reduced-motion: reduce)');
    expect(PATIENTS).not.toContain('prefers-reduced-motion');
    expect(PATIENTS).not.toMatch(/#[0-9a-f]{3,8}\b/i);
    expect(PATIENTS).not.toMatch(/\b(rgb|rgba|hsl|hsla)\(/i);
    expect(PATIENTS).not.toMatch(/(font-size|padding|margin|gap|border-radius):\s*-?\d*\.?\d+(px|rem|em)\b/i);
    expect(PATIENTS).toContain('var(--space-4)');
  });
});
