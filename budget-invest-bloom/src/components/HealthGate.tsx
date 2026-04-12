import { useEffect, useState, type ReactNode } from "react";
import * as Sentry from "@sentry/react";
import { Loader2 } from "lucide-react";

interface HealthGateProps {
  children: ReactNode;
}

const POLL_INTERVAL_MS = 2000;

/**
 * HealthGate polls the backend gateway's /actuator/health endpoint on mount
 * and blocks rendering of children until it receives a successful response.
 *
 * Spring Boot services in Docker can take 20-40 seconds to cold start,
 * so this prevents users from seeing blank/broken pages while the backend warms up.
 *
 * Uses raw fetch (not the apiRequest helper) to avoid auth-related side effects
 * like auto-redirect to /login on 401.
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
        await delay(POLL_INTERVAL_MS);
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
    <div className="dashboard-bg flex items-center justify-center min-h-screen">
      <div className="max-w-md p-8 glass-card text-center">
        <Loader2 className="w-10 h-10 text-emerald-400 animate-spin mx-auto mb-6" />
        <h1 className="text-2xl font-bold text-white mb-4">Сервер запускается...</h1>
        <p className="text-dashboard-text-muted">
          Это может занять до 30 секунд. Подожди немного.
        </p>
      </div>
    </div>
  );
};

export default HealthGate;
