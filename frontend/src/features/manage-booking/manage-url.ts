/** URL de la gestion de turno (design D4): solo el token en la ruta y, opcionalmente, `?accion=reprogramar`. */

export type ManageAction = 'detail' | 'reschedule';

const ACTION_PARAM = 'accion';
const RESCHEDULE_VALUE = 'reprogramar';

export function parseAction(query: URLSearchParams): ManageAction {
  return query.get(ACTION_PARAM) === RESCHEDULE_VALUE ? 'reschedule' : 'detail';
}

export function managePath(token: string, action: ManageAction = 'detail'): string {
  const base = `/turno/${encodeURIComponent(token)}`;
  return action === 'reschedule' ? `${base}?${ACTION_PARAM}=${RESCHEDULE_VALUE}` : base;
}
