import { describe, expect, it, vi } from 'vitest';
import { createPatientList, createPatientTable } from './patient-row';
import type { PatientSummary } from './patients-types';

const PATIENT: PatientSummary = {
  id: 'pac-001',
  firstName: '<script>',
  lastName: 'Simulada',
  dni: '30.456.789',
  phone: '11 5555 0001',
  active: true,
};

describe('patient-row - tabla desktop y lista móvil (D6)', () => {
  it('4.1 renderiza tabla con caption/encabezados y abre la ficha sin cambiar la URL', () => {
    const onOpen = vi.fn();
    const table = createPatientTable([PATIENT], onOpen);
    const caption = table.querySelector('caption');
    expect(caption?.classList.contains('sr-only')).toBe(true);
    expect(caption?.textContent).toContain('nombre, DNI y teléfono');
    expect([...table.querySelectorAll('thead th')].map((th) => th.getAttribute('scope'))).toEqual(['col', 'col', 'col']);
    expect(table.textContent).toContain('30.456.789');

    table.querySelector<HTMLAnchorElement>('.pname')?.click();
    expect(onOpen).toHaveBeenCalledWith('pac-001');
  });

  it('4.1/5.4 la lista móvil tiene etiqueta accesible y muestra el texto literal sin HTML', () => {
    const list = createPatientList([PATIENT], vi.fn());
    const link = list.querySelector<HTMLAnchorElement>('a');
    expect(list.getAttribute('aria-label')).toBe('Pacientes activos');
    expect(link?.getAttribute('aria-label')).toContain('<script>');
    expect(link?.textContent).toContain('<script> Simulada');
    expect(list.querySelector('script')).toBeNull();
    expect(list.querySelector('img')).toBeNull();
  });

  it('4.3 sin pacientes no genera filas ni enlaces', () => {
    expect(createPatientTable([], vi.fn()).querySelectorAll('tbody tr')).toHaveLength(0);
    expect(createPatientList([], vi.fn()).querySelectorAll('a')).toHaveLength(0);
  });
});
