import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { ANALYTICS_TAB_LABELS, analyticsPath, type AnalyticsTab } from '@/pages/analytics/analyticsFormat';

const TABS: AnalyticsTab[] = ['expenses', 'income', 'savings'];

interface YearArrowProps {
  direction: 'prev' | 'next';
  to: string | null;
}

function YearArrow({ direction, to }: YearArrowProps) {
  const Icon = direction === 'prev' ? ChevronLeft : ChevronRight;
  const label = direction === 'prev' ? 'Предыдущий год' : 'Следующий год';
  const className = cn(
    'flex shrink-0 items-center justify-center w-10 h-10 lg:w-[34px] lg:h-[34px] rounded-lg border border-app-border-strong transition-colors',
    to ? 'text-app-text hover:bg-app-surface-2' : 'text-app-text-dim opacity-40'
  );

  if (!to) {
    return (
      <button type="button" disabled aria-label={label} className={className}>
        <Icon aria-hidden="true" className="w-4 h-4" />
      </button>
    );
  }
  return (
    <Link to={to} aria-label={label} className={className}>
      <Icon aria-hidden="true" className="w-4 h-4" />
    </Link>
  );
}

interface TabLinkProps {
  tab: AnalyticsTab;
  activeTab: AnalyticsTab;
  year: number;
  className: string;
  activeClassName: string;
}

function TabLink({ tab, activeTab, year, className, activeClassName }: TabLinkProps) {
  const active = tab === activeTab;
  return (
    <Link to={analyticsPath(tab, year)} aria-current={active ? 'page' : undefined} className={active ? activeClassName : className}>
      {ANALYTICS_TAB_LABELS[tab]}
    </Link>
  );
}

interface AnalyticsHeaderProps {
  tab: AnalyticsTab;
  year: number;
  previousYear: number;
  earliestYear?: number | null;
}

export function AnalyticsHeader({ tab, year, previousYear, earliestYear }: AnalyticsHeaderProps) {
  const canGoNext = year < new Date().getFullYear();
  const canGoPrev = earliestYear != null && year > earliestYear;
  const prevTo = canGoPrev ? analyticsPath(tab, year - 1) : null;
  const nextTo = canGoNext ? analyticsPath(tab, year + 1) : null;

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-3 h-11">
        <div className="flex items-center gap-5">
          <h1 className="font-display text-[26px] lg:text-[34px] leading-none text-app-text">Аналитика</h1>
          <nav aria-label="Показатель" className="hidden lg:inline-flex items-center gap-1 p-1 rounded-[10px] bg-app-track">
            {TABS.map((item) => (
              <TabLink
                key={item}
                tab={item}
                activeTab={tab}
                year={year}
                className="h-8 px-3.5 rounded-[7px] inline-flex items-center text-[13px] font-medium text-app-text-muted"
                activeClassName="h-8 px-3.5 rounded-[7px] inline-flex items-center text-[13px] font-medium bg-app-surface text-app-text border border-app-border shadow-sm"
              />
            ))}
          </nav>
        </div>

        <div className="flex items-center gap-2.5 lg:gap-2">
          <YearArrow direction="prev" to={prevTo} />
          <span className="font-display text-[22px] lg:text-[26px] leading-none text-app-text min-w-[52px] lg:min-w-[70px] text-center">
            {year}
          </span>
          <YearArrow direction="next" to={nextTo} />
          <span className="hidden lg:inline text-app-text-dim text-[13px]">против {previousYear}</span>
        </div>
      </div>

      <nav aria-label="Показатель" className="flex lg:hidden gap-1 p-1 rounded-[10px] bg-app-track">
        {TABS.map((item) => (
          <TabLink
            key={item}
            tab={item}
            activeTab={tab}
            year={year}
            className="flex-1 h-9 rounded-[7px] inline-flex items-center justify-center text-[13px] font-medium text-app-text-muted"
            activeClassName="flex-1 h-9 rounded-[7px] inline-flex items-center justify-center text-[13px] font-medium bg-app-surface text-app-text border border-app-border"
          />
        ))}
      </nav>
    </div>
  );
}
