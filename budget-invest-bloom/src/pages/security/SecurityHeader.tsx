import { Link } from 'react-router-dom';
import { ArrowLeft, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { SecurityLogo } from '@/components/SecurityLogo';
import { formatDateTime, formatTime, NO_SECTOR_LABEL, signClass } from '@/pages/investments/investmentsFormat';
import { formatSignedPercent, formatUnitPrice } from '@/lib/dateOptions';
import { SECURITY_TYPE_LABEL_SINGULAR } from '@/lib/securityType';
import { SIGN_TEXT_CLASS } from '@/pages/security/securityFormat';
import type { SecurityPagePrice, SecurityPageSecurity } from '@/types/investment';

const CHIP_CLASS = 'h-[22px] px-2 rounded-full bg-app-surface-2 border border-app-border text-app-text-muted text-[11px] inline-flex items-center';

// Desktop line under the price (p.9): the stale notice, or today's move when the snapshot has a
// previous close to measure it against — never a made-up "+0,0%" without one
function PriceCaption({ price }: { price: SecurityPagePrice }) {
  if (price.stale) {
    return <span className="font-mono text-xs text-app-warn">{`цены на ${formatDateTime(price.asOf)}, биржа недоступна`}</span>;
  }
  const asOf = <span className="font-sans text-app-text-dim">цена на {formatTime(price.asOf)}</span>;
  if (price.dailyChangePercent == null) {
    return <span className="text-xs">{asOf}</span>;
  }
  return (
    <span className={`font-mono text-xs ${SIGN_TEXT_CLASS[signClass(price.dailyChangePercent)]}`}>
      {formatSignedPercent(price.dailyChangePercent)} сегодня <span className="font-sans text-app-text-dim">·</span> {asOf}
    </span>
  );
}

interface SecurityHeaderProps {
  security: SecurityPageSecurity;
  price?: SecurityPagePrice;
  onOpenTransactionDialog: () => void;
}

export function SecurityHeader({ security, price, onOpenTransactionDialog }: SecurityHeaderProps) {
  const sectorChip = security.historyStatus === 'PENDING' ? 'сектор загружается' : security.sector ?? NO_SECTOR_LABEL;

  return (
    <div className="flex items-center justify-between gap-3 lg:gap-4 min-h-[48px]">
      <div className="flex items-center gap-2.5 lg:gap-3.5 min-w-0">
        <Link
          to="/investments"
          aria-label="К инвестициям"
          className="flex items-center justify-center w-10 h-10 lg:w-[34px] lg:h-[34px] rounded-lg border border-app-border-strong bg-app-surface text-app-text-muted shrink-0 hover:bg-app-surface-2 transition-colors"
        >
          <ArrowLeft aria-hidden="true" className="w-4 h-4" />
        </Link>
        <span className="lg:hidden shrink-0">
          <SecurityLogo ticker={security.ticker} size={36} securityType={security.securityType} />
        </span>
        <span className="hidden lg:block shrink-0">
          <SecurityLogo ticker={security.ticker} size={44} securityType={security.securityType} />
        </span>
        <div className="min-w-0">
          <div className="flex items-baseline gap-2.5">
            <span className="font-display text-[22px] lg:text-[34px] leading-none text-app-text truncate">{security.name}</span>
            <span className="hidden lg:inline font-mono text-app-text-muted text-sm font-semibold shrink-0">{security.ticker}</span>
          </div>
          <div className="hidden lg:flex gap-1.5 mt-1.5">
            <span className={CHIP_CLASS}>{SECURITY_TYPE_LABEL_SINGULAR[security.securityType]}</span>
            <span className={CHIP_CLASS}>{sectorChip}</span>
            <span className={CHIP_CLASS}>{security.boardId ? `MOEX · ${security.boardId}` : 'MOEX'}</span>
          </div>
          <span className="lg:hidden block truncate font-mono text-app-text-muted text-[11px]">
            {security.ticker} · {sectorChip}
          </span>
        </div>
      </div>

      <div className="flex items-center gap-4 lg:gap-[18px] shrink-0">
        <div className="hidden lg:flex flex-col items-end gap-0.5">
          <span className="font-mono text-2xl font-semibold leading-none text-app-text">{price ? formatUnitPrice(price.current) : '—'}</span>
          {price && <PriceCaption price={price} />}
        </div>
        <Button size="sm" className="h-10 lg:h-[34px] gap-1.5 bg-app-accent text-app-accent-ink hover:bg-app-accent/90" onClick={onOpenTransactionDialog}>
          <Plus aria-hidden="true" className="w-3.5 h-3.5" />
          Сделка
        </Button>
      </div>
    </div>
  );
}
