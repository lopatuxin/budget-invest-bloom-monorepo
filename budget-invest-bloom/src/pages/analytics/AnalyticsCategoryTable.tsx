import { Link } from 'react-router-dom';
import { BudgetNormBadge } from '@/pages/budget/BudgetNormBadge';
import { AnalyticsContributionBar } from '@/pages/analytics/AnalyticsContributionBar';
import { formatContributionPoints } from '@/pages/analytics/analyticsFormat';
import { formatCurrency } from '@/lib/dateOptions';
import type { AnalyticsCategory, AnalyticsSection } from '@/types/budget';

const GRID_COLUMNS = 'minmax(0,1.4fr) 150px 150px 120px 190px 90px';

function ChangeCell({ category, previousYearHasData }: { category: AnalyticsCategory; previousYearHasData: boolean }) {
  if (!previousYearHasData) return <span className="text-app-text-muted">—</span>;
  if (category.change.status === 'NO_HISTORY') return <span className="text-app-text-dim">новая</span>;
  return <BudgetNormBadge deviationPercent={category.change.percent} status={category.change.status} variant="expense" />;
}

interface AnalyticsCategoryTableProps {
  categories: AnalyticsCategory[];
  expenses: AnalyticsSection;
  year: number;
  previousYear: number;
  previousYearHasData: boolean;
  personalInflationPercent: number | null;
}

export function AnalyticsCategoryTable({ categories, expenses, year, previousYear, previousYearHasData, personalInflationPercent }: AnalyticsCategoryTableProps) {
  const maxAbsPoints = Math.max(0, ...categories.map((c) => Math.abs(c.contributionPoints ?? 0)));

  return (
    <div className="glass-card p-2 lg:p-2 pt-3">
      <div className="flex items-baseline justify-between gap-2 px-3 pb-2.5">
        <h2 className="font-display text-[22px] text-app-text">Категории: {year} против {previousYear}</h2>
        <span className="text-app-text-dim text-xs text-right">
          {previousYearHasData ? 'средний месяц; вклад — сколько процентных пунктов категория добавила к личной инфляции' : `за ${previousYear} данных нет, сравнивать не с чем`}
        </span>
      </div>

      <div
        className="grid items-center h-[34px] px-3 text-[11px] font-semibold uppercase tracking-wide text-app-text-dim border-b border-app-border"
        style={{ gridTemplateColumns: GRID_COLUMNS }}
      >
        <span className="text-left">Категория</span>
        <span className="text-right">{year}, в месяц</span>
        <span className="text-right">{previousYear}, в месяц</span>
        <span className="text-right">Изменение</span>
        <span className="text-right">Вклад в инфляцию</span>
        <span className="text-right">Доля</span>
      </div>

      {categories.map((category) => (
        <div
          key={category.categoryId}
          className="grid items-center h-11 px-3 text-[13px] border-b border-app-border"
          style={{ gridTemplateColumns: GRID_COLUMNS }}
        >
          <div className="flex items-center gap-2.5 min-w-0 font-medium">
            <div className="w-7 h-7 rounded-lg bg-app-surface-2 border border-app-border flex items-center justify-center text-[15px] shrink-0">
              {category.emoji}
            </div>
            <Link to={`/budget/category/${encodeURIComponent(category.categoryName)}`} className="truncate text-app-text hover:underline">
              {category.categoryName}
            </Link>
          </div>
          <span className="font-mono text-right text-app-text">{formatCurrency(category.averageCurrent)}</span>
          <span className="font-mono text-right text-app-text-muted">{previousYearHasData ? formatCurrency(category.averagePrevious) : '—'}</span>
          <span className="text-right"><ChangeCell category={category} previousYearHasData={previousYearHasData} /></span>
          <span className="text-right">
            {category.contributionPoints != null ? (
              <AnalyticsContributionBar points={category.contributionPoints} maxAbsPoints={maxAbsPoints} />
            ) : (
              '—'
            )}
          </span>
          <span className="text-right text-app-text-muted">
            {category.sharePercent.toLocaleString('ru-RU', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%
          </span>
        </div>
      ))}

      <div className="grid items-center h-11 px-3 text-[13px] font-semibold border-t-2 border-app-border-strong" style={{ gridTemplateColumns: GRID_COLUMNS }}>
        <span>Все категории</span>
        <span className="font-mono text-right text-app-text">{expenses.average != null ? formatCurrency(expenses.average) : '—'}</span>
        <span className="font-mono text-right text-app-text-muted">{expenses.previousAverage != null ? formatCurrency(expenses.previousAverage) : '—'}</span>
        <span className="text-right"><BudgetNormBadge deviationPercent={expenses.change.percent} status={expenses.change.status} variant="expense" /></span>
        <span className="text-right font-mono text-app-text">{personalInflationPercent != null ? formatContributionPoints(personalInflationPercent) : '—'}</span>
        <span className="text-right text-app-text-muted">100%</span>
      </div>
    </div>
  );
}
