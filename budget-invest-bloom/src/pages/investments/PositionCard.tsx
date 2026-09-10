import { Link } from 'react-router-dom';
import { ArrowUpRight, ArrowDownRight } from 'lucide-react';
import { SecurityLogo } from '@/components/SecurityLogo';
import { formatCurrency, formatQuantity, formatSignedPercent, formatUnitPrice } from '@/lib/dateOptions';
import { formatPercent, signClass } from '@/pages/investments/investmentsFormat';
import type { PositionResponse } from '@/types/investment';

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

function PnlBadge({ percent, size }: { percent: number; size: 'sm' | 'xs' }) {
  const variant = signClass(percent);
  const Arrow = percent >= 0 ? ArrowUpRight : ArrowDownRight;
  const height = size === 'sm' ? 'h-5 px-1.5 text-xs' : 'h-[18px] px-1 text-[11px]';
  return (
    <span className={`inline-flex items-center gap-0.5 ${height} rounded-full font-mono font-semibold whitespace-nowrap shrink-0 ${BADGE_STYLES[variant]}`}>
      <Arrow aria-hidden="true" className="w-3 h-3" />
      {formatSignedPercent(percent)}
    </span>
  );
}

interface PositionCardProps {
  position: PositionResponse;
}

// Renders both the desktop (4-col grid, full detail) and mobile (2-col grid,
// compact) card variants, toggled with Tailwind visibility — same convention
// as OverviewCapitalChart, needed here because SecurityLogo takes a fixed
// pixel size that can't respond to a CSS breakpoint on its own.
export function PositionCard({ position }: PositionCardProps) {
  const nameLine = position.historyStatus === 'PENDING' ? 'сектор и история загружаются' : position.securityName;
  const dailyVariant = position.dailyChangePercent !== null ? signClass(position.dailyChangePercent) : 'neutral';
  const to = `/investments/security/${position.ticker}`;

  return (
    <>
      <Link
        to={to}
        className="hidden lg:flex flex-col gap-2 rounded-[10px] border border-app-border bg-app-surface py-3.5 px-4 hover:border-app-border-strong transition-colors"
      >
        <div className="flex items-center gap-2.5">
          <SecurityLogo ticker={position.ticker} size={36} securityType={position.securityType} />
          <div className="flex-1 min-w-0">
            <div className="font-mono text-sm font-semibold text-app-text">{position.ticker}</div>
            <div className="text-xs text-app-text-muted truncate">{nameLine}</div>
          </div>
          {position.pnlPercent !== null && <PnlBadge percent={position.pnlPercent} size="sm" />}
        </div>

        {position.currentValue !== null ? (
          <div className="flex items-center justify-between gap-2">
            <span className="font-mono text-xl font-semibold text-app-text">{formatCurrency(position.currentValue)}</span>
            {position.dailyChangePercent !== null && (
              <span className={`font-mono text-xs shrink-0 ${TEXT_STYLES[dailyVariant]}`}>
                {formatSignedPercent(position.dailyChangePercent)} сегодня
              </span>
            )}
          </div>
        ) : (
          <span className="text-app-text-dim text-sm">цена недоступна</span>
        )}

        {/* Holding size, and with it the per-share price — the value above is the whole
            position, so neither is visible anywhere else on the card. */}
        <span className="font-mono text-[11px] text-app-text-dim">
          {position.currentPrice !== null
            ? `${formatQuantity(position.quantity)} × ${formatUnitPrice(position.currentPrice)}`
            : `${formatQuantity(position.quantity)} шт.`}
        </span>

        {position.weightPercent !== null && (
          <div className="flex items-center gap-2">
            <div className="flex-1 h-1 rounded-full bg-app-track overflow-hidden">
              <div className="h-full rounded-full bg-app-neutral" style={{ width: `${position.weightPercent}%` }} />
            </div>
            <span className="font-mono text-[11px] text-app-text-dim shrink-0">{formatPercent(position.weightPercent)}</span>
          </div>
        )}
      </Link>

      <Link
        to={to}
        className="lg:hidden flex flex-col gap-1.5 rounded-[10px] border border-app-border bg-app-surface p-3"
      >
        <div className="flex items-center gap-2">
          <SecurityLogo ticker={position.ticker} size={28} securityType={position.securityType} />
          <span className="font-mono text-[13px] font-semibold text-app-text">{position.ticker}</span>
        </div>
        <div className="flex items-center justify-between gap-2">
          {position.currentValue !== null ? (
            <span className="font-mono text-[17px] font-semibold text-app-text">{formatCurrency(position.currentValue)}</span>
          ) : (
            <span className="text-app-text-dim text-sm">—</span>
          )}
          {position.pnlPercent !== null && <PnlBadge percent={position.pnlPercent} size="xs" />}
        </div>
        <span className="text-app-text-dim text-[11px]">
          {position.weightPercent !== null ? formatPercent(position.weightPercent) : '—'}
          {position.dailyChangePercent !== null && (
            <>
              {' · '}
              <span className={TEXT_STYLES[dailyVariant]}>{formatSignedPercent(position.dailyChangePercent)}</span> сегодня
            </>
          )}
          {position.currentValue === null && ' · цена недоступна'}
        </span>
      </Link>
    </>
  );
}
