import { ApiError } from '@/lib/api';
import type { RegisterFormValues } from '@/pages/auth/authSchemas';

export type AuthBannerVariant = 'bad' | 'warn';

export interface AuthBannerContent {
  variant: AuthBannerVariant;
  message: string;
}

export type RegisterFieldErrors = Partial<Record<keyof RegisterFormValues, string>>;

const NETWORK_ERROR_BANNER: AuthBannerContent = {
  variant: 'bad',
  message: 'Сервер не отвечает, попробуйте позже',
};

/** Bean-validation failures answer 400 with this code; the per-field Russian messages live in body.fields. */
const VALIDATION_ERROR_CODE = 'MISSING_REQUIRED_FIELDS';

const LOGIN_ERROR_BANNERS: Record<string, AuthBannerContent> = {
  INVALID_CREDENTIALS: { variant: 'bad', message: 'Неверная почта или пароль' },
  ACCOUNT_LOCKED: { variant: 'warn', message: 'Вход закрыт на 15 минут после пяти неудачных попыток' },
  ACCOUNT_INACTIVE: { variant: 'bad', message: 'Аккаунт отключён' },
  [VALIDATION_ERROR_CODE]: { variant: 'bad', message: 'Проверьте почту и пароль' },
};

const REGISTER_FIELDS: ReadonlyArray<keyof RegisterFormValues> = ['firstName', 'lastName', 'email', 'password'];

/** A 5xx body carries a generic English text ("Internal server error"), so the user gets the Russian fallback instead. */
function serverMessageOr(error: ApiError, fallback: string): string {
  if (error.status >= 500 || !error.message) return fallback;
  return error.message;
}

export function loginErrorBanner(error: unknown): AuthBannerContent {
  if (error instanceof ApiError) {
    if (error.code && LOGIN_ERROR_BANNERS[error.code]) {
      return LOGIN_ERROR_BANNERS[error.code];
    }
    return { variant: 'bad', message: serverMessageOr(error, 'Не удалось войти, попробуйте ещё раз') };
  }
  return NETWORK_ERROR_BANNER;
}

/**
 * Server errors that belong under a specific field: 409 means the email is taken,
 * a validation 400 carries its own message per field. Empty when the error is not field-bound.
 */
export function registerFieldErrors(error: unknown): RegisterFieldErrors {
  if (!(error instanceof ApiError)) return {};
  if (error.status === 409) {
    return { email: error.message };
  }
  if (error.code !== VALIDATION_ERROR_CODE) return {};

  // Keys come from FieldError.getField() on ApiRequest<RegisterRequest>, so they are nested: "data.email".
  const fields = (error.details as { fields?: Record<string, string> } | undefined)?.fields ?? {};
  const result: RegisterFieldErrors = {};
  for (const name of REGISTER_FIELDS) {
    const message = fields[`data.${name}`] ?? fields[name];
    if (message) result[name] = message;
  }
  return result;
}

export function registerErrorBanner(error: unknown): AuthBannerContent {
  if (error instanceof ApiError) {
    if (error.code === VALIDATION_ERROR_CODE) {
      return { variant: 'bad', message: 'Проверьте введённые данные' };
    }
    return { variant: 'bad', message: serverMessageOr(error, 'Не удалось создать аккаунт, попробуйте ещё раз') };
  }
  return NETWORK_ERROR_BANNER;
}
