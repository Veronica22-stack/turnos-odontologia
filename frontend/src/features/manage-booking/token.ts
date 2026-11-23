/**
 * Forma plausible del token de gestion (design D3): Base64URL de 20 a 128 caracteres. Rango amplio para
 * no romper si C-15 cambia la longitud; el servidor sigue siendo la autoridad. Funcion pura.
 */
const TOKEN_SHAPE = /^[A-Za-z0-9_-]{20,128}$/;

export function isPlausibleToken(value: string): boolean {
  return TOKEN_SHAPE.test(value);
}
