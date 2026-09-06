import { ArrowUpRight, ArrowDownRight } from 'lucide-react';
import type { NormStatus } from '@/types/budget';

// Full literal class strings (not interpolated) so Tailwind's content scanner keeps them.
const EXPENSE_STYLES: Record<Exclude<NormStatus, 'NO_HISTORY'>, string> = {
  ABOVE_MUCH: 'bg-app-bad-soft text-app-bad',
  ABOVE: 'bg-app-warn-soft text-app-warn',
  NORMAL: 'bg-app-neutral-soft text-app-neutral',
  BELOW: 'bg-app-good-soft text-app-good',
};

const INCOME_STYLES: Record<Exclude<NormStatus, 'NO_HISTORY'>, string> = {
  ABOVE_MUCH: 'bg-app-good-soft text-app-good',
  ABOVE: 'bg-app-good-soft text-app-good',
  NORMAL: 'bg-app-neutral-soft text-app-neutral',
  BELOW: 'bg-app-warn-soft text-app-warn',
};

function formatBadgeText(deviationPercent: number): string {
  if (Math.abs(deviationPercent) < 0.05) return '0%';
  const rounded = Math.round(deviationPercent);
  return `${rounded > 0 ? '+' : ''}${rounded}%`;
}

interface BudgetNormBadgeProps {
  deviationPercent?: number | null;
  status: NormStatus;
  variant: 'expense' | 'income';
}

export function BudgetNormBadge({ deviationPercent, status, variant }: BudgetNormBadgeProps) {
  if (status === 'NO_HISTORY' || deviationPercent == null) return null;

  const styles = variant === 'expense' ? EXPENSE_STYLES[status] : INCOME_STYLES[status];
  const Arrow = deviationPercent >= 0 ? ArrowUpRight : ArrowDownRight;

  return (
    <span
      className={`inline-flex items-center gap-0.5 h-5 px-1.5 rounded-full font-mono text-xs whitespace-nowrap ${styles}`}
    >
      <Arrow aria-hidden="true" className="w-3 h-3" />
      {formatBadgeText(deviationPercent)}
    </span>
  );
}
