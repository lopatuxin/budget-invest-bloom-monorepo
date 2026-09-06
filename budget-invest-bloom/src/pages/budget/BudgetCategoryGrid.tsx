import { Plus } from 'lucide-react';
import { BudgetCategoryCard } from '@/pages/budget/BudgetCategoryCard';
import type { BudgetPeriodKind } from '@/pages/budget/budgetPeriod';
import type { BudgetCategorySummary } from '@/types/budget';

interface BudgetCategoryGridProps {
  categories: BudgetCategorySummary[];
  dayOfMonth: number;
  periodKind: BudgetPeriodKind;
  onCategoryClick: (name: string) => void;
  onCreateCategory: () => void;
}

// Categories arrive from the backend already sorted by deviation — this component
// renders them in that order without re-sorting or filtering on the client.
export function BudgetCategoryGrid({
  categories,
  dayOfMonth,
  periodKind,
  onCategoryClick,
  onCreateCategory,
}: BudgetCategoryGridProps) {
  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-display text-[22px] text-app-text">Категории</h2>
        <span className="text-xs text-app-text-dim">по отклонению от обычного</span>
      </div>
      <div className="grid grid-cols-2 xl:grid-cols-3 gap-2.5 lg:gap-4">
        {categories.map((category) => (
          <BudgetCategoryCard
            key={category.id}
            category={category}
            dayOfMonth={dayOfMonth}
            periodKind={periodKind}
            onClick={() => onCategoryClick(category.name)}
          />
        ))}
        <button
          type="button"
          onClick={onCreateCategory}
          className="flex min-h-[120px] flex-col items-center justify-center gap-1.5 rounded-[10px] border border-dashed border-app-border-strong text-app-text-muted transition-colors hover:border-app-accent hover:text-app-accent"
        >
          <Plus aria-hidden="true" className="w-5 h-5" />
          <span className="text-sm font-medium">Новая категория</span>
        </button>
      </div>
    </div>
  );
}
