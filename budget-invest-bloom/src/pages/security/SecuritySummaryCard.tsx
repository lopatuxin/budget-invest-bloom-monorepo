import type { ReactNode } from 'react';
import { formatCurrency, formatDividendAmount, formatDividendDateLabel, formatSignedCurrency } from '@/lib/dateOptions';
import { formatPercent, signClass } from '@/pages/investments/investmentsFormat';
import { SecurityPnlBadge } from '@/pages/security/SecurityPnlBadge';
import { describePnlRow, SIGN_AMOUNT_CLASS } from '@/pages/security/securityFormat';
import type { SecurityPageDividends, SecurityPagePosition, SecurityPageResult } from '@/types/investment';

function SummaryRow({ label, value, valueClassName = 'font-mono text-app-text' }: { label: string; value: ReactNode; valueClassName?: string }) {
  return (
    <div className="flex items-center justify-between text-[13px]">
      <span className="text-app-text-muted">{label}</span>
      <span className={valueClassName}>{value}</span>
    </div>
  );
}

interface SecuritySummaryCardProps {
  position?: SecurityPagePosition;
  dividends: SecurityPageDividends;
  result: SecurityPageResult;
}

// Desktop «Итог» and «Ближайшая выплата» cards (p.12); the phone gets SecuritySummaryCompactCard
export function SecuritySummaryCard({ position, dividends, result }: SecuritySummaryCardProps) {
  const pnlRow = describePnlRow(position, result);

  return (
    <div className="hidden lg:flex flex-col gap-4">
      <div className="glass-card p-4 flex flex-col gap-3">
        <SummaryRow
          label="Стоимость"
          value={position?.currentValue != null ? formatCurrency(position.currentValue) : '—'}
          valueClassName="font-mono font-semibold text-app-text"
        />
        <SummaryRow label="Вложено" value={position ? formatCurrency(position.totalCost) : '—'} />

        <div className="flex flex-col gap-0.5">
          <div className="flex items-center justify-between text-[13px]">
            <span className="text-app-text-muted">{pnlRow.label}</span>
            <span className="flex items-center gap-1.5">
              <span className={`font-mono font-semibold ${SIGN_AMOUNT_CLASS[pnlRow.variant]}`}>{pnlRow.amountText}</span>
              {pnlRow.badgePercent != null && <SecurityPnlBadge percent={pnlRow.badgePercent} />}
            </span>
          </div>
          {!position && <span className="text-app-text-dim text-[11px]">позиция закрыта</span>}
        </div>

        <SummaryRow
          label="Дивиденды за 12 месяцев"
          value={
            <>
              {formatCurrency(dividends.total12m)}
              {dividends.yield12mPercent != null && (
                <span className="text-app-text-dim font-sans font-normal ml-1">· {formatPercent(dividends.yield12mPercent)} к вложенному</span>
              )}
            </>
          }
          valueClassName="font-mono font-semibold text-app-good"
        />
        <SummaryRow label="Дивиденды за всё время" value={formatCurrency(dividends.totalAll)} valueClassName="font-mono font-semibold text-app-good" />

        <div className="flex items-center justify-between text-[13px] border-t border-app-border pt-3">
          <span className="font-medium text-app-text">Итого</span>
          <span className="flex items-center gap-1.5">
            <span className={`font-mono font-semibold ${SIGN_AMOUNT_CLASS[signClass(result.total)]}`}>{formatSignedCurrency(result.total)}</span>
            {result.totalPercent != null && <SecurityPnlBadge percent={result.totalPercent} />}
          </span>
        </div>

        <SummaryRow label="Доля в портфеле" value={position?.weightPercent != null ? formatPercent(position.weightPercent) : '—'} />
      </div>

      <div className="glass-card p-4 flex flex-col gap-1.5">
        <span className="font-display text-[20px] text-app-text">Ближайшая выплата</span>
        {dividends.next ? (
          <>
            <span className="font-mono text-[22px] font-semibold text-app-text">
              {formatDividendAmount(dividends.next.netAmount, dividends.next.currency)}
            </span>
            <span className="text-app-text-muted text-xs">
              {formatDividendAmount(dividends.next.amountPerShare, dividends.next.currency, 'unit')} на акцию × {dividends.next.quantity} шт
              после НДФЛ · {formatDividendDateLabel(dividends.next, 'upcoming')}
            </span>
          </>
        ) : (
          <span className="text-app-text-muted text-sm">ближайших выплат нет</span>
        )}
      </div>
    </div>
  );
}
