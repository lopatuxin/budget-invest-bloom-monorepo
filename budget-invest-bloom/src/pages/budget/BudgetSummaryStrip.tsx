import { BudgetNormBadge } from '@/pages/budget/BudgetNormBadge';
import { BudgetNormBar } from '@/pages/budget/BudgetNormBar';
import type { BudgetPeriodKind } from '@/pages/budget/budgetPeriod';
import { formatCurrency } from '@/lib/dateOptions';
import type { NormComparison, NormStatus } from '@/types/budget';

interface BudgetSummaryStripProps {
  expenses: number;
  expenseNorm: NormComparison;
  income: number;
  incomeNorm: NormComparison;
  balance: number;
  dayOfMonth: number;
  periodKind: BudgetPeriodKind;
}

export function BudgetSummaryStrip({
  expenses,
  expenseNorm,
  income,
  incomeNorm,
  balance,
  dayOfMonth,
  periodKind,
}: BudgetSummaryStripProps) {
  const hasExpenseHistory =
    expenseNorm.status !== 'NO_HISTORY' && expenseNorm.averageMonthly !== null && expenseNorm.usualByDay !== null;
  const hasIncomeHistory = incomeNorm.status !== 'NO_HISTORY' && incomeNorm.averageMonthly !== null;

  const expenseSubtitle = !hasExpenseHistory
    ? 'нормы пока нет — нужен хотя бы один прошлый месяц с записями'
    : periodKind === 'past'
      ? `обычно за месяц — ${formatCurrency(expenseNorm.averageMonthly as number)}`
      : `обычно к ${dayOfMonth}-му — ${formatCurrency(expenseNorm.usualByDay as number)}, за полный месяц — ${formatCurrency(expenseNorm.averageMonthly as number)}`;

  const averageMonthly = expenseNorm.averageMonthly ?? 0;
  const fillPercent = hasExpenseHistory && averageMonthly > 0 ? (expenses / averageMonthly) * 100 : 0;
  const tickPercent = hasExpenseHistory && averageMonthly > 0 ? ((expenseNorm.usualByDay as number) / averageMonthly) * 100 : null;

  return (
    <div className="glass-card grid grid-cols-1 lg:grid-cols-[1.7fr_1fr_1fr] divide-y lg:divide-y-0 lg:divide-x divide-app-border p-5 gap-5 lg:gap-0">
      {/* Expenses */}
      <div className="flex flex-col gap-1.5 lg:pr-6">
        <p className="text-[13px] text-app-text-muted">Расходы за месяц</p>
        <div className="flex items-center gap-2 flex-wrap">
          <span className="font-mono text-[28px] lg:text-[30px] font-semibold text-app-text">{formatCurrency(expenses)}</span>
          <BudgetNormBadge deviationPercent={expenseNorm.deviationPercent} status={expenseNorm.status} variant="expense" />
        </div>
        <p className="text-[13px] text-app-text-muted">{expenseSubtitle}</p>
        {hasExpenseHistory && (
          <div className="flex flex-col gap-1 mt-1">
            <BudgetNormBar
              fillPercent={fillPercent}
              tickPercent={tickPercent}
              status={expenseNorm.status as Exclude<NormStatus, 'NO_HISTORY'>}
              height={8}
            />
            <p className="text-[11px] text-app-text-dim">
              полоса — обычный полный месяц · отметка — где вы обычно к сегодняшнему дню
            </p>
          </div>
        )}
      </div>

      {/* Income */}
      <div className="flex flex-col gap-1.5 lg:px-6 pt-5 lg:pt-0">
        <p className="text-[13px] text-app-text-muted">Доходы</p>
        <div className="flex items-center gap-2 flex-wrap">
          <span className="font-mono text-2xl font-semibold text-app-text">{formatCurrency(income)}</span>
          {hasIncomeHistory && (
            <BudgetNormBadge deviationPercent={incomeNorm.deviationPercent} status={incomeNorm.status} variant="income" />
          )}
        </div>
        {hasIncomeHistory && (
          <p className="text-[13px] text-app-text-muted">в среднем {formatCurrency(incomeNorm.averageMonthly as number)} в месяц</p>
        )}
      </div>

      {/* Balance */}
      <div className="flex flex-col gap-1.5 lg:pl-6 pt-5 lg:pt-0">
        <p className="text-[13px] text-app-text-muted">Свободно</p>
        <span className={`font-mono text-2xl font-semibold ${balance >= 0 ? 'text-app-good' : 'text-app-bad'}`}>
          {formatCurrency(balance)}
        </span>
        <p className="text-[13px] text-app-text-muted">доходы минус расходы</p>
      </div>
    </div>
  );
}
