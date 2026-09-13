import type { ReactNode } from 'react';
import { formatCurrency, formatDividendAmount, formatDividendDateLabel, formatQuantity, formatSignedCurrency } from '@/lib/dateOptions';
import { signClass } from '@/pages/investments/investmentsFormat';
import { SecurityPnlBadge } from '@/pages/security/SecurityPnlBadge';
import { describePnlRow, SIGN_AMOUNT_CLASS } from '@/pages/security/securityFormat';
import type { SecurityPageDividends, SecurityPagePosition, SecurityPageResult } from '@/types/investment';

function CompactRow({ label, children, isTotal = false }: { label: string; children: ReactNode; isTotal?: boolean }) {
  return (
    <div className={`flex items-center justify-between gap-3 min-h-[30px] text-[13px] ${isTotal ? 'border-t border-app-border' : ''}`}>
      <span className={isTotal ? 'font-medium text-app-text' : 'text-app-text-muted'}>{label}</span>
      <span className="flex items-center gap-1.5 font-mono text-right">{children}</span>
    </div>
  );
}

function DimNote({ children }: { children: ReactNode }) {
  return <span className="font-sans text-[11px] font-normal text-app-text-dim">{children}</span>;
}

interface SecuritySummaryCompactCardProps {
  position?: SecurityPagePosition;
  dividends: SecurityPageDividends;
  result: SecurityPageResult;
}

// Phone summary (p.14): one compact card above the timeline instead of the desktop pair
export function SecuritySummaryCompactCard({ position, dividends, result }: SecuritySummaryCompactCardProps) {
  const pnlRow = describePnlRow(position, result);

  return (
    <div className="lg:hidden glass-card px-3.5 py-2">
      <CompactRow label="Стоимость">
        <span className="font-semibold text-app-text">{position?.currentValue != null ? formatCurrency(position.currentValue) : '—'}</span>
        <DimNote>{position ? `${formatQuantity(position.quantity)} шт` : 'позиция закрыта'}</DimNote>
      </CompactRow>
      <CompactRow label="Вложено">
        <span className="text-app-text">{position ? formatCurrency(position.totalCost) : '—'}</span>
      </CompactRow>
      <CompactRow label={pnlRow.label}>
        <span className={`font-semibold ${SIGN_AMOUNT_CLASS[pnlRow.variant]}`}>{pnlRow.amountText}</span>
        {pnlRow.badgePercent != null && <SecurityPnlBadge percent={pnlRow.badgePercent} />}
      </CompactRow>
      <CompactRow label="Дивиденды за всё время">
        <span className="font-semibold text-app-good">{formatCurrency(dividends.totalAll)}</span>
      </CompactRow>
      <CompactRow label="Итого" isTotal>
        <span className={`font-semibold ${SIGN_AMOUNT_CLASS[signClass(result.total)]}`}>{formatSignedCurrency(result.total)}</span>
        {result.totalPercent != null && <SecurityPnlBadge percent={result.totalPercent} />}
      </CompactRow>
      <CompactRow label="Ближайшая выплата">
        {dividends.next ? (
          <>
            <span className="font-semibold text-app-text">{formatDividendAmount(dividends.next.netAmount, dividends.next.currency)}</span>
            <DimNote>{formatDividendDateLabel(dividends.next, 'upcoming')}</DimNote>
          </>
        ) : (
          <DimNote>нет</DimNote>
        )}
      </CompactRow>
    </div>
  );
}
