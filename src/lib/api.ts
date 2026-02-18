import * as Sentry from '@sentry/react';

interface ApiRequestOptions extends RequestInit {
  requiresAuth?: boolean;
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

  const requestHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(headers as Record<string, string>),
  };

  // Добавляем JWT токен, если требуется авторизация
  if (requiresAuth) {
    const accessToken = localStorage.getItem('accessToken');
    if (accessToken) {
      requestHeaders['Authorization'] = `Bearer ${accessToken}`;
    }
  }

  const url = `${import.meta.env.VITE_API_BASE_URL}${endpoint}`;
  const method = (restOptions.method || 'GET').toUpperCase();

  // Логируем начало запроса в Sentry breadcrumbs
  Sentry.addBreadcrumb({
    category: 'api.request',
    message: `${method} ${endpoint}`,
    level: 'info',
    data: {
      url,
      method,
      requiresAuth,
      // Не логируем тело запроса целиком (может быть большим или содержать чувствительные данные)
    },
  });

  const startTime = performance.now();

  try {
    const response = await fetch(url, {
      ...restOptions,
      headers: requestHeaders,
      credentials: 'include', // Всегда отправляем cookies (для refreshToken)
    });

    const duration = performance.now() - startTime;

    // Логируем ответ в Sentry breadcrumbs
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

    // Обработка ошибки 401 (токен истёк или невалиден)
    if (response.status === 401) {
      // Пытаемся обновить токен
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        // Повторяем запрос с новым токеном
        const newAccessToken = localStorage.getItem('accessToken');
        if (newAccessToken) {
          requestHeaders['Authorization'] = `Bearer ${newAccessToken}`;
        }
        const retryResponse = await fetch(url, {
          ...restOptions,
          headers: requestHeaders,
          credentials: 'include', // Отправляем cookies и в повторном запросе
        });

        if (!retryResponse.ok) {
          throw new Error(`API Error: ${retryResponse.status}`);
        }

        return await retryResponse.json();
      } else {
        // Не удалось обновить токен - перенаправляем на логин
        // refreshToken автоматически удаляется бекендом через Set-Cookie
        localStorage.removeItem('accessToken');
        localStorage.removeItem('user');
        globalThis.location.href = '/login';
        throw new Error('Session expired');
      }
    }

    if (!response.ok) {
      const errorMessage = `API Error: ${response.status} ${response.statusText}`;
      const apiError = new Error(errorMessage);

      // Отправляем ошибку в Sentry с дополнительным контекстом
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

      throw apiError;
    }

    return await response.json();
  } catch (error) {
    console.error('API Request failed:', error);

    // Если ошибка не была обработана выше, отправляем в Sentry
    if (error instanceof Error && !error.message.includes('API Error')) {
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

/**
 * Обновляет access токен используя refresh токен из Cookie
 * @returns true если токен успешно обновлён, false если нет
 */
async function refreshAccessToken(): Promise<boolean> {
  try {
    const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/auth/api/refresh`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include', // Важно: отправляем cookie с refreshToken
    });

    if (!response.ok) {
      return false;
    }

    const data = await response.json();
    const { accessToken } = data.body;

    // Сохраняем новый accessToken
    // refreshToken автоматически обновляется через Set-Cookie
    localStorage.setItem('accessToken', accessToken);

    return true;
  } catch (error) {
    console.error('Token refresh failed:', error);
    return false;
  }
}

// Вспомогательные функции для разных HTTP методов

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
    credentials: 'include', // Отправляем cookie с refreshToken
  });
}
