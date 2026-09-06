import { Button } from '@/components/ui/button';

interface RetryErrorCardProps {
  message: string;
  onRetry: () => void;
}

export function RetryErrorCard({ message, onRetry }: RetryErrorCardProps) {
  return (
    <div className="glass-card p-6 flex flex-col items-center gap-3 text-center">
      <p className="text-sm text-app-text-muted">{message}</p>
      <Button
        variant="outline"
        size="sm"
        onClick={() => onRetry()}
        className="border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2"
      >
        Повторить
      </Button>
    </div>
  );
}
