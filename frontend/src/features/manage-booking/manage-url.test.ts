import { describe, expect, it } from 'vitest';
import { managePath, parseAction } from './manage-url';

describe('parseAction', () => {
  it('reconoce accion=reprogramar', () => {
    expect(parseAction(new URLSearchParams('accion=reprogramar'))).toBe('reschedule');
  });

  it.each([
    ['ausente', ''],
    ['valor desconocido', 'accion=borrar'],
    ['mayúsculas', 'accion=REPROGRAMAR'],
    ['vacía', 'accion='],
    ['otra clave', 'paso=reprogramar'],
  ])('trata %s como detalle', (_label, query) => {
    expect(parseAction(new URLSearchParams(query))).toBe('detail');
  });
});

describe('managePath', () => {
  it('arma /turno/{token} para el detalle', () => {
    expect(managePath('tok_Abc-123')).toBe('/turno/tok_Abc-123');
  });

  it('agrega la query de reprogramar', () => {
    expect(managePath('tok_Abc-123', 'reschedule')).toBe('/turno/tok_Abc-123?accion=reprogramar');
  });

  it('codifica el token como segmento de ruta', () => {
    expect(managePath('a/b c')).toBe('/turno/a%2Fb%20c');
  });
});
