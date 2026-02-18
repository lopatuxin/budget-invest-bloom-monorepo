import { createRoot } from 'react-dom/client'
import React from 'react'
import * as Sentry from '@sentry/react'
import {
  useLocation,
  useNavigationType,
  createRoutesFromChildren,
  matchRoutes,
} from 'react-router-dom'
import App from './App.tsx'
import './index.css'

// Инициализация Sentry с Performance Monitoring
Sentry.init({
  dsn: import.meta.env.VITE_SENTRY_DSN,

  // Интеграции
  integrations: [
    // Browser Tracing для отслеживания производительности
    Sentry.browserTracingIntegration(),

    // Replay для записи сессий с ошибками
    Sentry.replayIntegration({
      maskAllText: true,
      blockAllMedia: true,
    }),

    // React Router интеграция
    Sentry.reactRouterV6BrowserTracingIntegration({
      useEffect: React.useEffect,
      useLocation,
      useNavigationType,
      createRoutesFromChildren,
      matchRoutes,
    }),
  ],

  // Performance Monitoring
  tracesSampleRate: import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE
    ? Number.parseFloat(import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE)
    : 1, // 100% в разработке, в проде лучше 0.1-0.3

  // Указываем origins для которых добавляем trace headers
  tracePropagationTargets: [
    'localhost',
    /^\//,
    import.meta.env.VITE_API_BASE_URL || '',
  ],

  // Session Replay
  replaysSessionSampleRate: 0.1, // 10% обычных сессий
  replaysOnErrorSampleRate: 1, // 100% сессий с ошибками

  // Окружение
  environment: import.meta.env.MODE,

  // Включаем только в production или если явно указан DSN
  enabled: import.meta.env.VITE_SENTRY_DSN !== undefined,

  // Дополнительная настройка
  beforeSend(event) {
    // Фильтруем чувствительные данные
    if (event.request?.headers) {
      delete event.request.headers['Authorization'];
    }

    // Можно добавить дополнительную логику фильтрации
    return event;
  },
});

const rootElement = document.getElementById("root");
if (!rootElement) {
  throw new Error("Root element not found");
}

createRoot(rootElement).render(<App />);
