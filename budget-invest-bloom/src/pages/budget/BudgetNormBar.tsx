import type { NormStatus } from '@/types/budget';

// Full literal class strings (not interpolated) so Tailwind's content scanner keeps them.
const FILL_COLOR: Record<Exclude<NormStatus, 'NO_HISTORY'>, string> = {
  ABOVE_MUCH: 'bg-app-bad',
  ABOVE: 'bg-app-warn',
  NORMAL: 'bg-app-neutral',
  BELOW: 'bg-app-good',
};

const clampPercent = (value: number): number => Math.min(Math.max(value, 0), 100);

interface BudgetNormBarProps {
  /** Fraction of the average full month spent so far, 0..100 */
  fillPercent: number;
  /** Where "usual by today" sits on the same scale, 0..100 — null hides the tick */
  tickPercent: number | null;
  status: Exclude<NormStatus, 'NO_HISTORY'>;
  height?: number;
}

export function BudgetNormBar({ fillPercent, tickPercent, status, height = 8 }: BudgetNormBarProps) {
  const tickHeight = height + 4;

  return (
    <div className="relative w-full" style={{ height: tickHeight }}>
      <div
        className="absolute inset-x-0 top-1/2 -translate-y-1/2 rounded-full bg-app-track overflow-hidden"
        style={{ height }}
      >
        <div className={`h-full rounded-full ${FILL_COLOR[status]}`} style={{ width: `${clampPercent(fillPercent)}%` }} />
      </div>
      {tickPercent !== null && (
        <div
          className="absolute top-0 w-0.5 bg-app-text rounded-full"
          style={{ left: `${clampPercent(tickPercent)}%`, height: tickHeight }}
        />
      )}
    </div>
  );
}
