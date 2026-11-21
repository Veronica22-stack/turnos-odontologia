export interface FieldError {
  readonly field: string;
  readonly code: string;
}

export type ApiError =
  | {
      readonly kind: 'problem';
      readonly status: number;
      readonly code: string;
      readonly errors: readonly FieldError[];
      readonly errorId?: string;
    }
  | { readonly kind: 'unauthorized' }
  | { readonly kind: 'network' }
  | { readonly kind: 'timeout' }
  | { readonly kind: 'invalid-response' };

export type HttpResult<T> = { readonly ok: true; readonly data: T } | { readonly ok: false; readonly error: ApiError };
