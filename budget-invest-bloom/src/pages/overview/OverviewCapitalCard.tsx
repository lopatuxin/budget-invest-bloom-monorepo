import { TrendingUp } from 'lucide-react';
import EmptyState from '@/components/EmptyState';
import { BudgetNormBadge } from '@/pages/budget/BudgetNormBadge';
import { OverviewCapitalChart } from '@/pages/overview/OverviewCapitalChart';
import { formatCurrency } from '@/lib/dateOptions';
import { formatSignedCurrency } from '@/pages/overview/overviewFormat';
import type { CapitalSection } from '@/types/budget';

interface OverviewCapitalCardProps {
  capital: CapitalSection;
  portfolioAvailable: boolean;
  hasNoRecords: boolean;
  onRecordExpense: () => void;
}

export function OverviewCapitalCard({ capital, portfolioAvailable, hasNoRecords, onRecordExpense }: OverviewCapitalCardProps) {
  const showBadge = capital.change.status !== 'NO_HISTORY' && capital.changeAbs != null;

  return (
    <div className="glass-card p-4 lg:p-6 grid grid-cols-1 lg:grid-cols-[380px_minmax(0,1fr)] gap-3 lg:gap-8 lg:items-center">
      <div className="flex flex-col gap-1.5 lg:gap-2">
        <span className="text-app-text-muted text-xs lg:text-[13px]">Капитал</span>
        <span className="font-mono text-[30px] lg:text-[40px] font-semibold leading-none text-app-text">
          {formatCurrency(capital.total)}
        </span>
        {showBadge && (
          <div className="flex items-center gap-2 lg:gap-2.5">
            <BudgetNormBadge deviationPercent={capital.change.percent} status={capital.change.status} variant="income" />
            <span className="text-app-text-muted text-xs lg:text-[13px]">
              {formatSignedCurrency(capital.changeAbs as number)} за 12 месяцев
            </span>
          </div>
        )}
        <span className="hidden lg:block text-app-text-muted text-[13px] mt-1">
          свободные деньги {formatCurrency(capital.freeMoney)} ·{' '}
          {portfolioAvailable ? `портфель ${formatCurrency(capital.portfolioValue)}` : 'портфель не учтён'}
        </span>
      </div>

      {hasNoRecords ? (
        <div className="h-[160px] lg:h-[200px]">
          <EmptyState
            icon={<TrendingUp className="w-10 h-10" />}
            title="Пока нет истории капитала"
            description="Запишите первый доход или расход — здесь появится траектория капитала"
            actionLabel="Записать расход"
            onAction={onRecordExpense}
          />
        </div>
      ) : (
        <div className="flex flex-col gap-1.5">
          <OverviewCapitalChart history={capital.history} />
          {capital.portfolioHistoryPending && (
            <span className="text-app-text-dim text-[11px]">
              история цен части бумаг ещё загружается, линия может измениться
            </span>
          )}
        </div>
      )}
    </div>
  );
}
