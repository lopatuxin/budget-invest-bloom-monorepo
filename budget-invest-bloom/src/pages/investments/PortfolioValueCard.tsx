import { ArrowUpRight, ArrowDownRight, TrendingUp } from 'lucide-react';
import EmptyState from '@/components/EmptyState';
import { PortfolioValueChart } from '@/pages/investments/PortfolioValueChart';
import { formatCurrency, formatSignedCurrency, formatSignedPercent, pluralSecurities } from '@/lib/dateOptions';
import { pluralSectors, signClass } from '@/pages/investments/investmentsFormat';
import type { PortfolioOverview } from '@/types/investment';

const BADGE_STYLES: Record<'good' | 'bad' | 'neutral', string> = {
  good: 'bg-app-good-soft text-app-good',
  bad: 'bg-app-bad-soft text-app-bad',
  neutral: 'bg-app-neutral-soft text-app-neutral',
};

const TEXT_STYLES: Record<'good' | 'bad' | 'neutral', string> = {
  good: 'text-app-good',
  bad: 'text-app-bad',
  neutral: 'text-app-text',
};

function DailyChangeBadge({ percent }: { percent: number }) {
  const variant = signClass(percent);
  const Arrow = percent >= 0 ? ArrowUpRight : ArrowDownRight;
  return (
    <span className={`inline-flex items-center gap-0.5 h-5 px-1.5 rounded-full font-mono text-xs whitespace-nowrap ${BADGE_STYLES[variant]}`}>
      <Arrow aria-hidden="true" className="w-3 h-3" />
      {formatSignedPercent(percent)}
    </span>
  );
}

interface PortfolioValueCardProps {
  overview: PortfolioOverview;
  isEmpty: boolean;
  onAddTransaction: () => void;
}

export function PortfolioValueCard({ overview, isEmpty, onAddTransaction }: PortfolioValueCardProps) {
  const dailyVariant = signClass(overview.dailyPnl);
  const totalVariant = signClass(overview.totalPnl);
  const dailyBadge = overview.dailyPnlPercent !== null ? <DailyChangeBadge percent={overview.dailyPnlPercent} /> : null;

  return (
    <div className="glass-card p-4 lg:p-6 grid grid-cols-1 lg:grid-cols-[360px_minmax(0,1fr)] gap-4 lg:gap-8 lg:items-center">
      <div className="flex flex-col gap-1.5 lg:gap-2">
        <span className="text-app-text-muted text-xs lg:text-[13px]">Стоимость портфеля</span>
        <span className="font-mono text-[30px] lg:text-[40px] font-semibold leading-none text-app-text">
          {formatCurrency(overview.totalValue)}
        </span>

        {!isEmpty && (
          <>
            {/* Desktop: badge + daily change only, "Результат"/"Вложено" sit in the grid below */}
            <div className="hidden lg:flex items-center gap-2.5 mt-0.5">
              {dailyBadge}
              <span className="text-app-text-muted text-[13px]">
                <span className={`font-mono ${TEXT_STYLES[dailyVariant]}`}>{formatSignedCurrency(overview.dailyPnl)}</span> сегодня
              </span>
            </div>

            {/* Mobile: badge + daily change + result combined into one line */}
            <div className="lg:hidden flex items-center gap-2 flex-wrap mt-0.5">
              {dailyBadge}
              <span className="text-app-text-muted text-xs">
                <span className={`font-mono ${TEXT_STYLES[dailyVariant]}`}>{formatSignedCurrency(overview.dailyPnl)}</span> сегодня · результат{' '}
                <span className={`font-mono ${TEXT_STYLES[totalVariant]}`}>{formatSignedCurrency(overview.totalPnl)}</span>
              </span>
            </div>

            <div className="hidden lg:grid grid-cols-2 gap-x-5 gap-y-2 mt-2 border-t border-app-border pt-3">
              <div className="flex flex-col gap-0.5">
                <span className="text-app-text-muted text-xs">Результат</span>
                <span className={`font-mono text-base font-semibold ${TEXT_STYLES[totalVariant]}`}>
                  {formatSignedCurrency(overview.totalPnl)}
                </span>
                <span className="text-app-text-dim text-xs">
                  {overview.totalPnlPercent !== null ? `${formatSignedPercent(overview.totalPnlPercent)} к вложенному` : 'нет данных'}
                </span>
              </div>
              <div className="flex flex-col gap-0.5">
                <span className="text-app-text-muted text-xs">Вложено</span>
                <span className="font-mono text-base font-semibold text-app-text">{formatCurrency(overview.totalCost)}</span>
                <span className="text-app-text-dim text-xs">
                  {pluralSecurities(overview.assetsCount)} · {pluralSectors(overview.sectorsCount)}
                </span>
              </div>
            </div>

            {overview.unpricedCount > 0 && (
              <span className="text-app-text-dim text-[11px] mt-1">без {pluralSecurities(overview.unpricedCount)} без цены</span>
            )}

            {/* Mobile mini chart lives inside the same left column, stacked below the numbers */}
            <div className="lg:hidden mt-2">
              <PortfolioValueChart variant="mobile" />
            </div>
          </>
        )}
      </div>

      <div className="hidden lg:block">
        {isEmpty ? (
          <div className="h-[180px]">
            <EmptyState
              icon={<TrendingUp className="w-10 h-10" />}
              title="Пока нет истории стоимости"
              description="Добавьте первую сделку — здесь появится график роста портфеля"
              actionLabel="Добавить сделку"
              onAction={onAddTransaction}
            />
          </div>
        ) : (
          <PortfolioValueChart variant="desktop" />
        )}
      </div>
    </div>
  );
}
