import { h } from '../../core/dom/h';

/**
 * Aviso de privacidad (Ley 25.326): texto generico, sin datos de personas.
 * Se reutiliza en el paso de datos de la reserva y en la ruta publica /privacidad.
 */
export function renderPrivacyNotice(): HTMLElement {
  return h(
    'div',
    { class: 'privacy-notice' },
    h(
      'p',
      {},
      'Este aviso explica cómo el consultorio trata tus datos personales, de acuerdo con la Ley 25.326 de Protección de los Datos Personales.',
    ),
    h('h2', {}, 'Para qué usamos tus datos'),
    h(
      'p',
      {},
      'Los usamos únicamente para gestionar tu turno: confirmarlo, permitirte cancelarlo o reprogramarlo y enviarte recordatorios por email.',
    ),
    h('h2', {}, 'Qué datos pedimos'),
    h('p', {}, 'Tu nombre y apellido, DNI, email y teléfono. No los cedemos a terceros ni los usamos con otros fines.'),
    h('h2', {}, 'Tus derechos'),
    h(
      'p',
      {},
      'Podés ejercer en cualquier momento los derechos de acceso, rectificación y supresión de tus datos comunicándote con el consultorio.',
    ),
    h(
      'p',
      {},
      'La Agencia de Acceso a la Información Pública (AAIP) es el órgano de control de la Ley 25.326 y atiende las denuncias y reclamos por incumplimientos.',
    ),
    h('h2', {}, 'Contacto'),
    h('p', {}, 'Para cualquier consulta sobre tus datos, comunicate con el consultorio.'),
  );
}
