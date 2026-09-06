import { ReactNode } from 'react';

interface EmptyStateProps {
  icon: ReactNode;
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
}

const EmptyState = ({ icon, title, description, actionLabel, onAction }: EmptyStateProps) => (
  <div className="h-full flex items-center justify-center">
    <div className="flex flex-col items-center gap-3 text-center px-4">
      <div className="text-dashboard-text-muted opacity-50">
        {icon}
      </div>
      <p className="text-sm font-medium text-dashboard-text">{title}</p>
      <p className="text-xs text-dashboard-text-muted max-w-[220px]">{description}</p>
      {actionLabel && onAction && (
        <button
          onClick={onAction}
          className="mt-1 px-4 py-2 rounded-lg text-xs font-medium bg-app-accent-soft text-app-accent hover:bg-app-accent hover:text-app-accent-ink transition-colors"
        >
          {actionLabel}
        </button>
      )}
    </div>
  </div>
);

export default EmptyState;
