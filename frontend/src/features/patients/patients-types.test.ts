import { describe, expect, it } from 'vitest';
import { decodePatientDetail, decodePatientSummaries, decodePatientSummary, isLocalDate } from './patients-types';

const SUMMARY = {
  id: 'pac-001',
  firstName: 'Julieta',
  lastName: 'Simulada',
  dni: '00.111.111',
  phone: '11 5555 0001',
  active: true,
};

const DETAIL = {
  ...SUMMARY,
  email: 'julieta@ejemplo.invalid',
  birthDate: '1992-04-18',
  address: 'Calle Falsa 123',
  source: 'MANUAL',
  createdAt: '2025-03-12T12:41:00Z',
  updatedAt: '2026-11-18T17:07:00Z',
};

describe('patients-types - decodificadores del contrato C-10 (D2/D3)', () => {
  it('decodifica un resumen y un detalle completos sin inventar campos', () => {
    expect(decodePatientSummary(SUMMARY)).toEqual(SUMMARY);
    expect(decodePatientSummaries([SUMMARY, { ...SUMMARY, id: 'pac-002', active: false }])).toEqual([
      SUMMARY,
      { ...SUMMARY, id: 'pac-002', active: false },
    ]);
    expect(decodePatientDetail(DETAIL)).toEqual(DETAIL);
  });

  it('conserva nulls y decodifica el marcador opcional sin inferirlo del origen', () => {
    const withoutFlag = { ...DETAIL, source: 'PUBLIC_BOOKING', email: null, birthDate: null, address: null };
    const decoded = decodePatientDetail(withoutFlag);

    expect(decoded).toEqual(withoutFlag);
    expect(decoded).not.toHaveProperty('reviewRequired');
    expect(decodePatientDetail({ ...DETAIL, reviewRequired: true })?.reviewRequired).toBe(true);
    expect(decodePatientDetail({ ...DETAIL, reviewRequired: false })?.reviewRequired).toBe(false);
  });

  it('rechaza campos malformados y tipos inesperados sin lanzar', () => {
    expect(decodePatientSummary({ ...SUMMARY, dni: 123 })).toBeUndefined();
    expect(decodePatientSummary(null)).toBeUndefined();
    expect(decodePatientSummaries([SUMMARY, { ...SUMMARY, phone: null }])).toBeUndefined();
    expect(decodePatientSummaries({})).toBeUndefined();
    expect(decodePatientDetail({ ...DETAIL, source: 'OTRO' })).toBeUndefined();
    expect(decodePatientDetail({ ...DETAIL, createdAt: '2025-03-12T12:41:00' })).toBeUndefined();
    expect(decodePatientDetail({ ...DETAIL, birthDate: 19920418 })).toBeUndefined();
    expect(decodePatientDetail({ ...DETAIL, email: undefined })).toBeUndefined();
    expect(decodePatientDetail(42)).toBeUndefined();
  });

  it('valida fechas calendario para el formulario sin depender de la zona horaria', () => {
    expect(isLocalDate('1992-04-18')).toBe(true);
    expect(isLocalDate('2026-02-30')).toBe(false);
    expect(isLocalDate('2026-2-03')).toBe(false);
    expect(isLocalDate(null)).toBe(false);
  });
});
