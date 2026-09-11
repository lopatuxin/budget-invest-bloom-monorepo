import { Link } from 'react-router-dom';
import { BudgetNormBadge } from '@/pages/budget/BudgetNormBadge';
import { formatCurrency } from '@/lib/dateOptions';
import type { AnalyticsCategory, AnalyticsSection } from '@/types/budget';

function ChangeBadge({ category, previousYearHasData }: { category: AnalyticsCategory; previousYearHasData: boolean }) {
  if (!previousYearHasData) return <span className="w-[62px] text-center text-app-text-muted text-xs">—</span>;
  if (category.change.status === 'NO_HISTORY') {
    return <span className="w-[62px] text-center text-app-text-dim text-xs">новая</span>;
  }
  return (
    <span className="w-[62px] flex justify-center">
      <BudgetNormBadge deviationPercent={category.change.percent} status={category.change.status} variant="expense" />
    </span>
  );
}

interface AnalyticsCategoryListProps {
  categories: AnalyticsCategory[];
  expenses: AnalyticsSection;
  previousYear: number;
  previousYearHasData: boolean;
}

// Mobile counterpart of AnalyticsCategoryTable: same rows, no contribution bar or share column.
export function AnalyticsCategoryList({ categories, expenses, previousYear, previousYearHasData }: AnalyticsCategoryListProps) {
  return (
    <div className="glass-card overflow-hidden">
      <div className="flex items-baseline justify-between gap-2 px-3 pt-3 pb-2">
        <h2 className="font-display text-[18px] text-app-text">Категории</h2>
        <span className="text-app-text-dim text-[11px]">в месяц · к {previousYear}</span>
      </div>

      {categories.map((category) => (
        <div key={category.categoryId} className="flex items-center gap-2.5 h-12 px-3 border-t border-app-border">
          <div className="w-7 h-7 rounded-lg bg-app-surface-2 border border-app-border flex items-center justify-center text-sm shrink-0">
            {category.emoji}
          </div>
          <Link to={`/budget/category/${encodeURIComponent(category.categoryName)}`} className="flex-1 min-w-0 truncate font-medium text-[13px] text-app-text">
            {category.categoryName}
          </Link>
          <span className="font-mono text-[13px] text-app-text">{formatCurrency(category.averageCurrent)}</span>
          <ChangeBadge category={category} previousYearHasData={previousYearHasData} />
        </div>
      ))}

      <div className="flex items-center gap-2.5 h-12 px-3 border-t border-app-border font-semibold">
        <span className="flex-1 min-w-0 truncate text-[13px] text-app-text">Все категории</span>
        <span className="font-mono text-[13px] text-app-text">{expenses.average != null ? formatCurrency(expenses.average) : '—'}</span>
        <span className="w-[62px] flex justify-center">
          <BudgetNormBadge deviationPercent={expenses.change.percent} status={expenses.change.status} variant="expense" />
        </span>
      </div>
    </div>
  );
}
