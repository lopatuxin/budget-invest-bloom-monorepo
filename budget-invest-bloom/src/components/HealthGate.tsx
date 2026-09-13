import { useEffect, useState, type ReactNode } from "react";
import * as Sentry from "@sentry/react";
import { Loader2, Sprout } from "lucide-react";

interface HealthGateProps {
  children: ReactNode;
}

const INITIAL_POLL_INTERVAL_MS = 2000;
const MAX_POLL_INTERVAL_MS = 30_000;

/**
 * HealthGate polls the backend's /actuator/health endpoint on mount
 * and blocks rendering of children until it receives a successful response.
 *
 * Spring Boot services in Docker can take 20-40 seconds to cold start,
 * so this prevents users from seeing blank/broken pages while the backend warms up.
 *
 * Uses raw fetch (not the apiRequest helper) to avoid auth-related side effects
 * like auto-redirect to /login on 401.
 *
 * Uses exponential backoff on failures: interval doubles on each failure, capped at 30s.
 * Resets to the initial interval on success.
 */
const HealthGate = ({ children }: HealthGateProps) => {
  const [isHealthy, setIsHealthy] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    const startedAt = Date.now();
    // Explicit fallback: when VITE_API_BASE_URL is not set, use empty string
    // so the request goes to the same origin via a relative path.
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";
    let timeoutId: ReturnType<typeof setTimeout> | undefined;

    const checkHealth = async (): Promise<boolean> => {
      try {
        const response = await fetch(`${apiBaseUrl}/actuator/health`, {
          method: "GET",
          signal,
        });
        return response.ok;
      } catch {
        // Network error or abort — backend not ready yet
        return false;
      }
    };

    // Delay that resolves early if the abort signal fires,
    // so the polling loop terminates immediately on unmount.
    const delay = (ms: number): Promise<void> =>
      new Promise((resolve) => {
        timeoutId = setTimeout(resolve, ms);
        signal.addEventListener(
          "abort",
          () => {
            if (timeoutId !== undefined) {
              clearTimeout(timeoutId);
            }
            resolve();
          },
          { once: true }
        );
      });

    const poll = async () => {
      let interval = INITIAL_POLL_INTERVAL_MS;

      while (!signal.aborted) {
        const healthy = await checkHealth();
        if (signal.aborted) return;

        if (healthy) {
          const elapsedSeconds = Math.round((Date.now() - startedAt) / 1000);
          // Only log a breadcrumb if we actually had to wait (>1s)
          if (elapsedSeconds >= 1) {
            Sentry.addBreadcrumb({
              category: "health",
              level: "info",
              message: `Backend became healthy after ${elapsedSeconds} seconds`,
            });
          }
          setIsHealthy(true);
          return;
        }

        // Exponential backoff: double interval on each failure, cap at MAX_POLL_INTERVAL_MS
        await delay(interval);
        interval = Math.min(interval * 2, MAX_POLL_INTERVAL_MS);
      }
    };

    void poll();

    return () => {
      controller.abort();
      if (timeoutId !== undefined) {
        clearTimeout(timeoutId);
      }
    };
  }, []);

  if (isHealthy) {
    return <>{children}</>;
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-app-bg p-4">
      <div className="max-w-[360px] flex flex-col items-center gap-4 text-center">
        <div className="w-11 h-11 rounded-xl bg-app-accent flex items-center justify-center">
          <Sprout aria-hidden="true" className="w-[18px] h-[18px] text-app-accent-ink" />
        </div>
        <h1 className="font-display text-[28px] text-app-text">Сервер запускается</h1>
        <p className="text-[13px] text-app-text-muted">Обычно это занимает до 30 секунд. Страница откроется сама.</p>
        <Loader2 aria-hidden="true" className="w-7 h-7 text-app-accent animate-spin" />
      </div>
    </div>
  );
};

export default HealthGate;
