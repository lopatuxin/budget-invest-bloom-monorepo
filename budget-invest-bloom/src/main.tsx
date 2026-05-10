import '@fontsource-variable/dm-sans';
import '@fontsource-variable/jetbrains-mono';
import '@fontsource-variable/manrope';
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

/** Auth endpoints whose bodies and URLs must be scrubbed from Sentry events */
const SENTRY_AUTH_PATHS = [
  '/auth/api/login',
  '/auth/api/register',
  '/auth/api/refresh',
  '/auth/api/forgot-password',
];

function isAuthUrl(url: string | undefined): boolean {
  if (!url) return false;
  return SENTRY_AUTH_PATHS.some((p) => url.includes(p));
}

/** Strip query string, keep only path */
function pathOnly(url: string): string {
  try {
    return new URL(url).pathname;
  } catch {
    return url.split('?')[0];
  }
}

// Sentry initialization with Performance Monitoring
Sentry.init({
  dsn: import.meta.env.VITE_SENTRY_DSN,

  integrations: [
    // Browser Tracing for performance monitoring
    Sentry.browserTracingIntegration(),

    // Replay for recording sessions with errors
    Sentry.replayIntegration({
      maskAllText: true,
      blockAllMedia: true,
      maskAllInputs: true,
    }),

    // React Router integration
    Sentry.reactRouterV6BrowserTracingIntegration({
      useEffect: React.useEffect,
      useLocation,
      useNavigationType,
      createRoutesFromChildren,
      matchRoutes,
    }),
  ],

  // In production sample 10% of traces; sample everything in development
  tracesSampleRate: import.meta.env.PROD
    ? (import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE
        ? Number.parseFloat(import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE)
        : 0.1)
    : 1,

  // Origins for which trace headers are added
  tracePropagationTargets: [
    'localhost',
    /^\//,
    import.meta.env.VITE_API_BASE_URL || '',
  ],

  // Session Replay
  replaysSessionSampleRate: 0.1, // 10% of regular sessions
  replaysOnErrorSampleRate: 1,   // 100% of sessions with errors

  environment: import.meta.env.MODE,

  // Enable only in production or when a DSN is explicitly provided
  enabled: import.meta.env.VITE_SENTRY_DSN !== undefined,

  beforeSend(event) {
    // Remove Authorization header
    if (event.request?.headers) {
      delete event.request.headers['Authorization'];
    }

    // Remove cookies entirely
    if (event.request) {
      delete event.request.cookies;
    }

    // Scrub body and URL for auth endpoints
    if (event.request && isAuthUrl(event.request.url)) {
      event.request.data = null;
    }

    // Scrub auth-related breadcrumbs
    if (event.breadcrumbs?.values) {
      event.breadcrumbs.values = event.breadcrumbs.values.map((bc) => {
        const bcUrl: string | undefined =
          (bc.data as Record<string, string> | undefined)?.url;
        if (isAuthUrl(bcUrl)) {
          return {
            ...bc,
            data: {
              ...bc.data,
              body: undefined,
              url: bcUrl ? pathOnly(bcUrl) : undefined,
            },
          };
        }
        return bc;
      });
    }

    return event;
  },
});

const rootElement = document.getElementById("root");
if (!rootElement) {
  throw new Error("Root element not found");
}

createRoot(rootElement).render(<App />);
