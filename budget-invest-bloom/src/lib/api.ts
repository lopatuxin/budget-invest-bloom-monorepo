import * as Sentry from '@sentry/react';

// TODO security: подтвердить SameSite=Strict для refresh cookie на бэке
// и добавить CSRF double-submit token для мутирующих запросов.

interface ApiRequestOptions extends RequestInit {
  requiresAuth?: boolean;
}

/** Extended Error that carries the server-side error code (e.g. 'INVALID_CREDENTIALS') */
export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** Auth endpoints whose request bodies must never appear in logs or Sentry */
const AUTH_ENDPOINTS = [
  '/auth/api/login',
  '/auth/api/register',
  '/auth/api/refresh',
  '/auth/api/forgot-password',
];

function isAuthEndpoint(endpoint: string): boolean {
  return AUTH_ENDPOINTS.some((p) => endpoint.startsWith(p));
}

/**
 * Returns server message if it looks safe to show to the user.
 * Falls back to a generic message when the string is too long or looks like a stack trace.
 */
function sanitizeErrorMessage(serverMessage: string | undefined, fallback: string): string {
  if (!serverMessage) return fallback;
  const dangerous = [
    'Exception',
    'at org.',
    '\tat ',
    'SQLException',
    'NullPointer',
  ];
  if (
    serverMessage.length > 200 ||
    dangerous.some((kw) => serverMessage.includes(kw))
  ) {
    return fallback;
  }
  return serverMessage;
}

function statusFallback(status: number): string {
  if (status >= 400 && status < 500) {
    if (status === 400) return 'Некорректный запрос';
    if (status === 401) return 'Необходима авторизация';
    if (status === 403) return 'Доступ запрещён';
    if (status === 404) return 'Ресурс не найден';
    return 'Ошибка запроса';
  }
  return 'Серверная ошибка, попробуйте позже';
}

/**
 * Выполняет HTTP запрос к API с автоматическим добавлением JWT токена
 * @param endpoint - путь к endpoint (например, '/api/budget/categories')
 * @param options - опции fetch запроса
 * @returns Promise с ответом
 */
export async function apiRequest<T = unknown>(
  endpoint: string,
  options: ApiRequestOptions = {}
): Promise<T> {
  const { requiresAuth = true, headers = {}, ...restOptions } = options;
  const isAuth = isAuthEndpoint(endpoint);

  const requestHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(headers as Record<string, string>),
  };

  // Add JWT token when required
  if (requiresAuth) {
    const accessToken = localStorage.getItem('accessToken');
    if (accessToken) {
      requestHeaders['Authorization'] = `Bearer ${accessToken}`;
    }
  }

  const url = `${import.meta.env.VITE_API_BASE_URL}${endpoint}`;
  const method = (restOptions.method || 'GET').toUpperCase();

  // Log request start in Sentry breadcrumbs — never include body for auth endpoints
  Sentry.addBreadcrumb({
    category: 'api.request',
    message: `${method} ${endpoint}`,
    level: 'info',
    data: {
      url,
      method,
      requiresAuth,
      // Never log request body — may contain sensitive data
    },
  });

  const startTime = performance.now();

  try {
    const response = await fetch(url, {
      ...restOptions,
      headers: requestHeaders,
      credentials: 'include', // Always send cookies (for refreshToken)
    });

    const duration = performance.now() - startTime;

    // Log response in Sentry breadcrumbs
    Sentry.addBreadcrumb({
      category: 'api.response',
      message: `${response.status} ${method} ${endpoint}`,
      level: response.ok ? 'info' : 'warning',
      data: {
        status: response.status,
        statusText: response.statusText,
        duration: `${duration.toFixed(2)}ms`,
        url,
      },
    });

    // Handle 401 (token expired or invalid)
    if (response.status === 401) {
      // Attempt to refresh the token
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        // Retry the original request with the new token
        const newAccessToken = localStorage.getItem('accessToken');
        if (newAccessToken) {
          requestHeaders['Authorization'] = `Bearer ${newAccessToken}`;
        }
        const retryResponse = await fetch(url, {
          ...restOptions,
          headers: requestHeaders,
          credentials: 'include',
        });

        if (!retryResponse.ok) {
          // Retry also failed — treat as expired session
          window.dispatchEvent(new CustomEvent('auth:expired'));
          throw new Error(`Request failed after token refresh: ${retryResponse.status}`);
        }

        return await retryResponse.json();
      } else {
        // Could not refresh — signal session expiry via event; AuthContext handles the redirect
        localStorage.removeItem('accessToken');
        localStorage.removeItem('user');
        window.dispatchEvent(new CustomEvent('auth:expired'));
        throw new Error('Session expired');
      }
    }

    if (!response.ok) {
      // Try to extract the server message and error code from the response body
      let serverMessage: string | undefined;
      let serverErrorCode: string | undefined;
      let serverBody: unknown;
      try {
        const errorBody = await response.json();
        serverMessage = errorBody?.message;
        serverErrorCode = errorBody?.error;
        serverBody = errorBody?.body;
      } catch {
        // Response body is not valid JSON — fall back to statusText
      }

      const errorMessage = sanitizeErrorMessage(serverMessage, statusFallback(response.status));
      const apiError = new ApiError(errorMessage, response.status, serverErrorCode, serverBody);

      // For auth endpoints, only send status code to Sentry — never the message
      if (!isAuth) {
        Sentry.captureException(apiError, {
          level: 'error',
          tags: {
            api_endpoint: endpoint,
            api_method: method,
            api_status: response.status,
          },
          contexts: {
            api: {
              url,
              endpoint,
              method,
              status: response.status,
              statusText: response.statusText,
              requiresAuth,
            },
          },
        });
      } else {
        Sentry.captureException(new Error(`Auth endpoint error`), {
          level: 'warning',
          tags: {
            api_endpoint: endpoint,
            api_method: method,
            api_status: response.status,
          },
        });
      }

      throw apiError;
    }

    return await response.json();
  } catch (error) {
    // For auth endpoints, log only minimal info to avoid leaking credentials
    if (isAuth) {
      const status = error instanceof Error ? error.message : 'unknown';
      console.warn('Auth request failed', status);
    } else {
      console.error('API Request failed:', error);
    }

    // For non-auth network/unexpected errors, report to Sentry
    // ApiError instances are already reported above; skip re-reporting them
    if (!isAuth && error instanceof Error && !(error instanceof ApiError) && !error.message.includes('Session expired')) {
      Sentry.captureException(error, {
        level: 'error',
        tags: {
          api_endpoint: endpoint,
          api_method: method,
        },
        contexts: {
          api: {
            url,
            endpoint,
            method,
            requiresAuth,
            errorType: 'network_or_unexpected',
          },
        },
      });
    }

    throw error;
  }
}

// Single-flight guard: all concurrent 401 handlers share one pending refresh promise
let refreshPromise: Promise<boolean> | null = null;

/**
 * Обновляет access токен используя refresh токен из Cookie.
 * Использует single-flight: параллельные вызовы получают один и тот же Promise,
 * чтобы не отправлять несколько запросов /auth/api/refresh одновременно.
 * @returns true если токен успешно обновлён, false если нет
 */
async function refreshAccessToken(): Promise<boolean> {
  if (refreshPromise !== null) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    try {
      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/auth/api/refresh`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include', // Important: send cookie with refreshToken
      });

      if (!response.ok) {
        return false;
      }

      const data = await response.json();
      const { accessToken } = data.body;

      // Store the new accessToken
      // refreshToken is updated automatically via Set-Cookie
      localStorage.setItem('accessToken', accessToken);

      return true;
    } catch (error) {
      console.warn('Token refresh failed:', error instanceof Error ? error.message : 'network error');
      return false;
    }
  })().finally(() => {
    // Reset so the next 401 can initiate a fresh refresh
    refreshPromise = null;
  });

  return refreshPromise;
}

// Helper functions for different HTTP methods

export function apiGet<T = unknown>(endpoint: string, options?: ApiRequestOptions): Promise<T> {
  return apiRequest<T>(endpoint, { ...options, method: 'GET' });
}

export function apiPost<T = unknown>(endpoint: string, data?: unknown, options?: ApiRequestOptions): Promise<T> {
  return apiRequest<T>(endpoint, {
    ...options,
    method: 'POST',
    body: data ? JSON.stringify({ data }) : undefined,
  });
}

export function apiPut<T = unknown>(endpoint: string, data?: unknown, options?: ApiRequestOptions): Promise<T> {
  return apiRequest<T>(endpoint, {
    ...options,
    method: 'PUT',
    body: data ? JSON.stringify({ data }) : undefined,
  });
}

export function apiDelete<T = unknown>(endpoint: string, options?: ApiRequestOptions): Promise<T> {
  return apiRequest<T>(endpoint, { ...options, method: 'DELETE' });
}

/**
 * Выполняет logout пользователя
 * @param logoutFromAll - если true, выход со всех устройств
 * @returns Promise с ответом от сервера
 */
export async function apiLogout(logoutFromAll: boolean = false): Promise<{
  message: string;
  loggedOut: number;
  timestamp: string;
}> {
  return apiRequest('/auth/api/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ data: { logoutFromAll } }),
    credentials: 'include', // Send cookie with refreshToken
  });
}
