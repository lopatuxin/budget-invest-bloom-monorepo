import { BudgetNormBadge } from '@/pages/budget/BudgetNormBadge';
import { BudgetNormBar } from '@/pages/budget/BudgetNormBar';
import type { BudgetPeriodKind } from '@/pages/budget/budgetPeriod';
import { formatCurrency } from '@/lib/dateOptions';
import { cn } from '@/lib/utils';
import type { BudgetCategorySummary, NormStatus } from '@/types/budget';

interface BudgetCategoryCardProps {
  category: BudgetCategorySummary;
  dayOfMonth: number;
  periodKind: BudgetPeriodKind;
  onClick: () => void;
}

export function BudgetCategoryCard({ category, dayOfMonth, periodKind, onClick }: BudgetCategoryCardProps) {
  const { norm } = category;
  const hasHistory = norm.status !== 'NO_HISTORY' && norm.averageMonthly !== null && norm.usualByDay !== null;

  const subtitle = !hasHistory
    ? 'раньше к этому дню трат не было'
    : periodKind === 'past'
      ? `обычно за месяц — ${formatCurrency(norm.averageMonthly as number)}`
      : `обычно к ${dayOfMonth}-му — ${formatCurrency(norm.usualByDay as number)}`;

  const averageMonthly = norm.averageMonthly ?? 0;
  const fillPercent = hasHistory && averageMonthly > 0 ? (category.amount / averageMonthly) * 100 : 0;
  const tickPercent = hasHistory && averageMonthly > 0 ? ((norm.usualByDay as number) / averageMonthly) * 100 : null;

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex flex-col gap-2.5 rounded-[10px] border bg-app-surface p-4 text-left transition-colors hover:border-app-border-strong',
        norm.status === 'ABOVE_MUCH' ? 'border-app-bad' : 'border-app-border'
      )}
    >
      <div className="flex items-center gap-2.5">
        <div className="w-8 h-8 rounded-lg bg-app-surface-2 flex items-center justify-center text-base shrink-0">
          {category.emoji || category.name.charAt(0).toUpperCase()}
        </div>
        <p className="flex-1 min-w-0 text-sm font-medium text-app-text truncate">{category.name}</p>
        <BudgetNormBadge deviationPercent={norm.deviationPercent} status={norm.status} variant="expense" />
      </div>
      <p className="font-mono text-[22px] font-semibold text-app-text">{formatCurrency(category.amount)}</p>
      <p className="text-xs text-app-text-muted">{subtitle}</p>
      {hasHistory && (
        <div className="flex items-center gap-2">
          <div className="flex-1">
            <BudgetNormBar
              fillPercent={fillPercent}
              tickPercent={tickPercent}
              status={norm.status as Exclude<NormStatus, 'NO_HISTORY'>}
              height={6}
            />
          </div>
          <span className="font-mono text-[11px] text-app-text-dim shrink-0">{formatCurrency(averageMonthly)}</span>
        </div>
      )}
    </button>
  );
}
