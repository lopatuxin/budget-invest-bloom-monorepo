import { BudgetNormBadge } from '@/pages/budget/BudgetNormBadge';
import { formatCurrency } from '@/lib/dateOptions';
import { formatSignedPercent } from '@/pages/overview/overviewFormat';
import type { Totals12m, TotalsLine } from '@/types/budget';

interface TotalsRowProps {
  label: string;
  line: TotalsLine;
  variant: 'income' | 'expense';
}

function TotalsRow({ label, line, variant }: TotalsRowProps) {
  return (
    <div className="flex items-center justify-between h-10 lg:h-11">
      <span className="text-app-text-muted">{label}</span>
      <span className="inline-flex items-center gap-2">
        <span className="font-mono font-semibold text-app-text">{formatCurrency(line.amount)}</span>
        <BudgetNormBadge deviationPercent={line.change.percent} status={line.change.status} variant={variant} />
      </span>
    </div>
  );
}

interface OverviewYearTotalsProps {
  totals12m: Totals12m;
  personalInflationPercent: number | null;
  isCurrentMonthPartial: boolean;
  currentMonthLabel: string;
}

export function OverviewYearTotals({
  totals12m,
  personalInflationPercent,
  isCurrentMonthPartial,
  currentMonthLabel,
}: OverviewYearTotalsProps) {
  return (
    <div className="glass-card p-4 lg:p-5 flex flex-col h-full">
      <div className="flex items-baseline justify-between gap-2 mb-1 lg:mb-2">
        <h2 className="font-display text-[20px] lg:text-[22px] text-app-text whitespace-nowrap shrink-0">За 12 месяцев</h2>
        <span className="text-app-text-dim text-[11px] lg:text-xs text-right">
          против предыдущих 12{isCurrentMonthPartial ? ` · ${currentMonthLabel} неполный` : ''}
        </span>
      </div>

      <div className="flex flex-col divide-y divide-app-border">
        <TotalsRow label="Доходы" line={totals12m.income} variant="income" />
        <TotalsRow label="Расходы" line={totals12m.expenses} variant="expense" />
        <TotalsRow label="Сбережено" line={totals12m.saved} variant="income" />
        <div className="flex items-center justify-between h-10 lg:h-11">
          <span className="text-app-text-muted">Личная инфляция</span>
          <span className="font-mono font-semibold text-app-text">
            {personalInflationPercent == null ? '—' : formatSignedPercent(personalInflationPercent)}
          </span>
        </div>
      </div>

      <span className="text-app-text-dim text-[11px] mt-2">личная инфляция — средний месяц этого года против прошлого</span>
    </div>
  );
}
