import { BudgetNormBadge } from '@/pages/budget/BudgetNormBadge';
import { BudgetNormBar } from '@/pages/budget/BudgetNormBar';
import { getBudgetPeriodKind } from '@/pages/budget/budgetPeriod';
import {
  categoryBarCaption,
  categorySpentSubtitle,
  hasNormHistory,
  operationsTileLabel,
  operationsTileSubtitle,
  shareSubtitle,
  shareValue,
  spentTileLabel,
  usualPerMonthSubtitle,
  usualPerMonthValue,
} from '@/pages/category/categoryFormat';
import { formatCurrency } from '@/lib/dateOptions';
import type { CategoryPageResponse, NormStatus } from '@/types/budget';

interface CategorySummaryStripProps {
  data: CategoryPageResponse;
}

export function CategorySummaryStrip({ data }: CategorySummaryStripProps) {
  const { norm, spent, dayOfMonth, normMonthsCounted, sharePercent, operationsCount, averageCheck, largestAmount, period } = data;
  // Derived from the response's own period, not the address bar: on a month switch,
  // keepPreviousData keeps `data` (this whole object, period included) on the previous month
  // until the new one arrives, so periodKind here always describes the numbers it sits next
  // to. The header above already shows the address's month — it just switched there.
  const periodKind = getBudgetPeriodKind(period.month, period.year);
  // Future months carry no norm to compare against, whatever the backend's status happens to be.
  const showNorm = periodKind !== 'future';
  const hasHistory = showNorm && hasNormHistory(norm);
  const averageMonthly = norm.averageMonthly ?? 0;
  const fillPercent = hasHistory && averageMonthly > 0 ? (spent / averageMonthly) * 100 : 0;
  const tickPercent =
    hasHistory && periodKind === 'current' && averageMonthly > 0 ? ((norm.usualByDay as number) / averageMonthly) * 100 : null;

  return (
    <div className="glass-card flex flex-col gap-5 p-5 lg:grid lg:grid-cols-[1.7fr_1fr_1fr_1fr] lg:divide-x lg:divide-app-border lg:gap-0">
      <div className="flex flex-col gap-1.5 lg:pr-6">
        <p className="text-[13px] text-app-text-muted">{spentTileLabel(period.month)}</p>
        <div className="flex items-center gap-2 flex-wrap">
          <span className="font-mono text-[28px] lg:text-[30px] font-semibold text-app-text">{formatCurrency(spent)}</span>
          {showNorm && <BudgetNormBadge deviationPercent={norm.deviationPercent} status={norm.status} variant="expense" />}
        </div>
        <p className="text-[13px] text-app-text-muted">
          <span className="lg:hidden">{categorySpentSubtitle(periodKind, norm, dayOfMonth, 'compact')}</span>
          <span className="hidden lg:inline">{categorySpentSubtitle(periodKind, norm, dayOfMonth, 'full')}</span>
        </p>
        {hasHistory && (
          <div className="mt-1 flex flex-col gap-1">
            <BudgetNormBar
              fillPercent={fillPercent}
              tickPercent={tickPercent}
              status={norm.status as Exclude<NormStatus, 'NO_HISTORY'>}
              height={8}
            />
            <p className="text-[11px] text-app-text-dim">
              <span className="lg:hidden">{categoryBarCaption(periodKind, norm, 'compact')}</span>
              <span className="hidden lg:inline">{categoryBarCaption(periodKind, norm, 'full')}</span>
            </p>
          </div>
        )}
      </div>

      <div className="hidden lg:flex flex-col gap-1.5 px-6">
        <span className="text-[13px] text-app-text-muted">Обычно в месяц</span>
        <span className="font-mono text-2xl font-semibold text-app-text">{usualPerMonthValue(norm)}</span>
        <span className="text-[13px] text-app-text-muted">{usualPerMonthSubtitle(norm, normMonthsCounted)}</span>
      </div>
      <div className="hidden lg:flex flex-col gap-1.5 px-6">
        <span className="text-[13px] text-app-text-muted">Доля в расходах</span>
        <span className="font-mono text-2xl font-semibold text-app-text">{shareValue(sharePercent)}</span>
        <span className="text-[13px] text-app-text-muted">{shareSubtitle()}</span>
      </div>
      <div className="hidden lg:flex flex-col gap-1.5 pl-6">
        <span className="text-[13px] text-app-text-muted">{operationsTileLabel(period.month)}</span>
        <span className="font-mono text-2xl font-semibold text-app-text">{operationsCount}</span>
        <span className="text-[13px] text-app-text-muted">{operationsTileSubtitle(operationsCount, averageCheck, largestAmount)}</span>
      </div>

      <div className="grid grid-cols-2 gap-2.5 lg:hidden">
        <div className="rounded-[10px] border border-app-border bg-app-surface p-3 flex flex-col gap-1">
          <span className="text-xs text-app-text-muted">Доля в расходах</span>
          <span className="font-mono text-lg font-semibold text-app-text">{shareValue(sharePercent)}</span>
          <span className="text-[11px] text-app-text-muted">{shareSubtitle('compact')}</span>
        </div>
        <div className="rounded-[10px] border border-app-border bg-app-surface p-3 flex flex-col gap-1">
          <span className="text-xs text-app-text-muted">Операций</span>
          <span className="font-mono text-lg font-semibold text-app-text">{operationsCount}</span>
          <span className="text-[11px] text-app-text-muted">
            {operationsTileSubtitle(operationsCount, averageCheck, largestAmount, 'compact')}
          </span>
        </div>
      </div>
    </div>
  );
}
