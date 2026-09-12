import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';
import { monthYearLabel } from '@/lib/monthNames';

interface MonthSwitcherProps {
  month: number;
  year: number;
  subtitle: string;
  isLoading?: boolean;
  onPrevMonth: () => void;
  onNextMonth: () => void;
}

const ARROW_BUTTON_CLASS =
  'flex shrink-0 items-center justify-center w-10 h-10 lg:w-[34px] lg:h-[34px] rounded-lg border border-app-border-strong text-app-text hover:bg-app-surface-2 transition-colors';

export function MonthSwitcher({ month, year, subtitle, isLoading, onPrevMonth, onNextMonth }: MonthSwitcherProps) {
  const monthLabel = monthYearLabel(month, year);

  return (
    <div className="flex items-center gap-3">
      <button type="button" aria-label="Предыдущий месяц" onClick={onPrevMonth} className={ARROW_BUTTON_CLASS}>
        <ChevronLeft aria-hidden="true" className="w-4 h-4" />
      </button>
      <div className="flex flex-col items-center min-w-[170px] lg:min-w-[186px]">
        <span className="font-display text-[22px] lg:text-[26px] leading-tight text-app-text">{monthLabel}</span>
        {isLoading ? (
          <Skeleton className="lg:hidden h-3 w-20 mt-1 bg-app-border" />
        ) : (
          <p className="lg:hidden text-[11px] text-app-text-dim mt-0.5">{subtitle}</p>
        )}
      </div>
      <button type="button" aria-label="Следующий месяц" onClick={onNextMonth} className={ARROW_BUTTON_CLASS}>
        <ChevronRight aria-hidden="true" className="w-4 h-4" />
      </button>
      {isLoading ? (
        <Skeleton className="hidden lg:block h-3.5 w-24 bg-app-border" />
      ) : (
        <p className="hidden lg:block text-[13px] text-app-text-dim">{subtitle}</p>
      )}
    </div>
  );
}
