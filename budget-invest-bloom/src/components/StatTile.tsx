import type { ReactNode } from 'react';

interface StatTileProps {
  label: string;
  // Usually a formatted string; a colored span for a value whose sign needs a color
  // (e.g. negative savings) is also valid — see AnalyticsTiles.
  value: ReactNode;
  valueExtra?: ReactNode;
  subtitle: ReactNode;
}

export function StatTile({ label, value, valueExtra, subtitle }: StatTileProps) {
  return (
    <div className="glass-card p-3 lg:p-4 flex flex-col gap-1 lg:gap-1.5">
      <span className="text-app-text-muted text-xs lg:text-[13px]">{label}</span>
      <div className="flex items-center gap-1.5 lg:gap-2">
        <span className="font-mono text-lg lg:text-[22px] font-semibold text-app-text">{value}</span>
        {valueExtra}
      </div>
      <span className="text-app-text-muted text-[11px] lg:text-xs">{subtitle}</span>
    </div>
  );
}
