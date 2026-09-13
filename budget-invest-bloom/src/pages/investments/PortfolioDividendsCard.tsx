import { useState } from 'react';
import { SecurityLogo } from '@/components/SecurityLogo';
import { formatCurrency, formatDividendAmount, formatDividendDateLabel, formatQuantity } from '@/lib/dateOptions';
import { formatPercent, formatTaxRatePercent } from '@/pages/investments/investmentsFormat';
import type { UpcomingDividend } from '@/types/investment';

// Matches PortfolioTransactionsCard's RECENT_TRANSACTIONS_LIMIT — recentDividends
// itself already covers the full 12 months (no backend limit), so "show all"
// here just reveals rows already in memory instead of fetching more.
const VISIBLE_ROWS_LIMIT = 5;

interface DividendRowProps {
  dividend: UpcomingDividend;
  variant: 'upcoming' | 'received';
}

function DividendRow({ dividend, variant }: DividendRowProps) {
  return (
    <div className="flex items-center gap-2.5 h-11">
      <SecurityLogo ticker={dividend.ticker} size={28} />
      <div className="flex-1 min-w-0">
        <span className="font-mono text-sm font-semibold text-app-text">{dividend.ticker}</span>
        <span className="text-app-text-muted text-xs ml-2">
          {formatDividendAmount(dividend.amountPerShare, dividend.currency, 'unit')} × {formatQuantity(dividend.quantity)}
        </span>
      </div>
      <span className="font-mono text-[13px] font-semibold text-app-text shrink-0">
        {formatDividendAmount(dividend.totalAmount, dividend.currency)}
      </span>
      <span className="text-app-text-dim text-xs w-[120px] text-right shrink-0">{formatDividendDateLabel(dividend, variant)}</span>
    </div>
  );
}

interface PortfolioDividendsCardProps {
  upcomingDividends: UpcomingDividend[];
  recentDividends: UpcomingDividend[];
  dividends12m: number;
  dividendYieldPercent: number | null;
  dividendTaxRatePercent: number;
  dividendsSourceConfigured: boolean;
}

export function PortfolioDividendsCard({
  upcomingDividends,
  recentDividends,
  dividends12m,
  dividendYieldPercent,
  dividendTaxRatePercent,
  dividendsSourceConfigured,
}: PortfolioDividendsCardProps) {
  const [expanded, setExpanded] = useState(false);
  const isEmpty = upcomingDividends.length === 0 && recentDividends.length === 0;
  // The card lists every payout across the portfolio, dividend-paying stocks and
  // coupon-paying bonds alike, so the heading names both once a coupon shows up (p.8).
  const hasCoupons = upcomingDividends.some((d) => d.kind === 'COUPON') || recentDividends.some((d) => d.kind === 'COUPON');

  // Upcoming first, then recent — same order the backend already returns each
  // list in; this only interleaves the two for the shared row limit.
  const allRows = [
    ...upcomingDividends.map((d) => ({ d, group: 'upcoming' as const })),
    ...recentDividends.map((d) => ({ d, group: 'received' as const })),
  ];
  const visibleRows = expanded ? allRows : allRows.slice(0, VISIBLE_ROWS_LIMIT);

  return (
    <div className="glass-card p-4 lg:p-5 flex flex-col">
      <div className="flex items-baseline justify-between mb-1.5">
        <h2 className="font-display text-[20px] lg:text-[22px] text-app-text">{hasCoupons ? 'Дивиденды и купоны' : 'Дивиденды'}</h2>
        <span className="text-app-text-dim text-xs">
          за 12 месяцев <span className="font-mono text-app-text font-semibold">{formatCurrency(dividends12m)}</span>
          {dividendYieldPercent !== null && <> · {formatPercent(dividendYieldPercent)} к вложенному</>}
        </span>
      </div>

      {!dividendsSourceConfigured && (
        <p className="text-app-text-dim text-xs mb-2">источник дивидендов не настроен</p>
      )}

      {dividendTaxRatePercent > 0 && (
        <p className="text-app-text-dim text-xs mb-2">суммы за вычетом НДФЛ {formatTaxRatePercent(dividendTaxRatePercent)}</p>
      )}

      {isEmpty ? (
        <p className="text-app-text-muted text-sm py-4">выплат за год не было</p>
      ) : (
        <div className="flex flex-col divide-y divide-app-border">
          {visibleRows.map(({ d, group }) => (
            <DividendRow key={`${group}-${d.ticker}-${d.recordDate}`} dividend={d} variant={group} />
          ))}
        </div>
      )}

      {!expanded && allRows.length > VISIBLE_ROWS_LIMIT && (
        <button type="button" onClick={() => setExpanded(true)} className="text-app-accent text-[13px] mt-3 text-left w-fit hover:underline">
          все выплаты
        </button>
      )}

      {!isEmpty && (
        <span className="hidden lg:block text-app-text-dim text-[11px] mt-3">
          предстоящие выплаты первыми, затем последние полученные
        </span>
      )}
    </div>
  );
}
