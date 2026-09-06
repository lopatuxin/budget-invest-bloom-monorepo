import { useMemo } from 'react';
import { ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import type { BudgetPeriodKind } from '@/pages/budget/budgetPeriod';

interface BudgetMonthHeaderProps {
  month: number;
  year: number;
  dayOfMonth: number;
  daysInMonth: number;
  periodKind: BudgetPeriodKind;
  isLoading: boolean;
  onPrevMonth: () => void;
  onNextMonth: () => void;
  onOpenCategoryDialog: () => void;
}

const MONTH_YEAR_FORMAT = new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric' });

export function BudgetMonthHeader({
  month,
  year,
  dayOfMonth,
  daysInMonth,
  periodKind,
  isLoading,
  onPrevMonth,
  onNextMonth,
  onOpenCategoryDialog,
}: BudgetMonthHeaderProps) {
  const monthLabel = useMemo(() => {
    // Intl appends "г." (year abbreviation) in ru-RU — strip it, the mockup shows "Сентябрь 2026".
    const raw = MONTH_YEAR_FORMAT.format(new Date(year, month - 1, 1)).replace(/\s*г\.$/i, '');
    return raw.charAt(0).toUpperCase() + raw.slice(1);
  }, [month, year]);

  const subtitle =
    periodKind === 'current'
      ? `${dayOfMonth}-й день из ${daysInMonth}`
      : periodKind === 'past'
        ? 'полный месяц'
        : 'месяц ещё не начался';

  return (
    <div className="flex items-center justify-between gap-3">
      <div className="flex items-center gap-3 mx-auto lg:mx-0 lg:flex-1 lg:justify-center">
        <button
          type="button"
          aria-label="Предыдущий месяц"
          onClick={onPrevMonth}
          className="flex shrink-0 items-center justify-center w-11 h-11 lg:w-[34px] lg:h-[34px] rounded-lg border border-app-border-strong text-app-text hover:bg-app-surface-2 transition-colors"
        >
          <ChevronLeft aria-hidden="true" className="w-4 h-4" />
        </button>
        <div className="flex flex-col items-center lg:flex-row lg:gap-3">
          <h1 className="font-display text-[26px] lg:text-[34px] leading-tight text-app-text text-center lg:min-w-[250px]">
            {monthLabel}
          </h1>
          {isLoading ? (
            <Skeleton className="h-3.5 w-24 mt-1 lg:mt-0 bg-app-border" />
          ) : (
            <p className="text-[11px] lg:text-[13px] text-app-text-dim mt-0.5 lg:mt-0">{subtitle}</p>
          )}
        </div>
        <button
          type="button"
          aria-label="Следующий месяц"
          onClick={onNextMonth}
          className="flex shrink-0 items-center justify-center w-11 h-11 lg:w-[34px] lg:h-[34px] rounded-lg border border-app-border-strong text-app-text hover:bg-app-surface-2 transition-colors"
        >
          <ChevronRight aria-hidden="true" className="w-4 h-4" />
        </button>
      </div>
      <Button variant="ghost" size="sm" className="hidden lg:inline-flex shrink-0 gap-1.5 text-app-accent hover:bg-app-accent-soft hover:text-app-accent" onClick={onOpenCategoryDialog}>
        <Plus aria-hidden="true" className="w-4 h-4" />
        Категория
      </Button>
    </div>
  );
}
