const MASK = '•••';
const VISIBLE_LOCAL = 2;

/**
 * Enmascara un email para la confirmacion (design D6): primeros 2 caracteres de la parte local,
 * `•••` y el dominio completo; con parte local de 1 o 2 caracteres, solo el primero.
 */
export function maskEmail(email: string): string {
  const at = email.lastIndexOf('@');
  if (at <= 0) {
    return MASK;
  }
  const local = Array.from(email.slice(0, at));
  const visible = local.length > VISIBLE_LOCAL ? VISIBLE_LOCAL : 1;
  return `${local.slice(0, visible).join('')}${MASK}${email.slice(at)}`;
}
