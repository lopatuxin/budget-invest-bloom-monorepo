import { Button } from '@/components/ui/button';

interface AppErrorFallbackProps {
  resetError: () => void;
}

// Sentry.ErrorBoundary fallback for the whole app — shown when a component throws during render.
export function AppErrorFallback({ resetError }: AppErrorFallbackProps) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-app-bg p-4">
      <div className="glass-card w-full max-w-[420px] p-7 text-center flex flex-col items-center gap-3">
        <h1 className="font-display text-[28px] text-app-text">Что-то пошло не так</h1>
        <p className="text-[13px] text-app-text-muted">Ошибка уже отправлена, страницу можно открыть заново.</p>
        <Button variant="outline" onClick={resetError}>
          Попробовать снова
        </Button>
      </div>
    </div>
  );
}
