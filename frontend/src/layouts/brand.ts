// Efecto lateral: design-system/brand.js publica `window.BRAND` (fuente unica del nombre visible).
import '../../../design-system/brand.js';
import logoUrl from '../../../design-system/assets/logo.svg?url';
import { isRecord, isString } from '../core/http/decode';

const FALLBACK_BRAND_NAME = 'La Muela';

export { logoUrl };

export function brandName(): string {
  const brand: unknown = Reflect.get(window, 'BRAND');
  if (isRecord(brand) && isString(brand['name']) && brand['name'] !== '') {
    return brand['name'];
  }
  return FALLBACK_BRAND_NAME;
}

/** Mientras el logo sea provisional (README del design system), el alt lo dice. */
export function logoAlt(): string {
  return `${brandName()} (logo provisional)`;
}
