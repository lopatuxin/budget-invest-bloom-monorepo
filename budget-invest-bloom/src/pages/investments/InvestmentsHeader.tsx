import { Link } from 'react-router-dom';
import { LineChart, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { formatDateTime, formatTime } from '@/pages/investments/investmentsFormat';

interface InvestmentsHeaderProps {
  pricesAsOf: string | null;
  pricesStale: boolean;
  onAddTransaction: () => void;
}

export function InvestmentsHeader({ pricesAsOf, pricesStale, onAddTransaction }: InvestmentsHeaderProps) {
  const pricesLabel = pricesAsOf
    ? pricesStale
      ? `цены на ${formatDateTime(pricesAsOf)}, биржа недоступна`
      : `цены на ${formatTime(pricesAsOf)}`
    : null;

  return (
    <div className="flex items-center justify-between h-11">
      <div className="flex flex-col lg:flex-row lg:items-baseline gap-0.5 lg:gap-3.5">
        <h1 className="font-display text-[26px] lg:text-[34px] leading-none text-app-text">Инвестиции</h1>
        {pricesLabel && (
          <span className={`text-[11px] lg:text-[13px] ${pricesStale ? 'text-app-warn' : 'text-app-text-dim'}`}>
            {pricesLabel}
          </span>
        )}
      </div>
      <div className="flex items-center gap-2">
        <Button asChild variant="outline" size="sm" className="hidden lg:inline-flex gap-1.5 border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2">
          <Link to="/investments/forecast">
            <LineChart aria-hidden="true" className="w-3.5 h-3.5" />
            Прогноз
          </Link>
        </Button>
        <Button size="sm" className="hidden lg:inline-flex gap-1.5 bg-app-accent text-app-accent-ink hover:bg-app-accent/90" onClick={onAddTransaction}>
          <Plus aria-hidden="true" className="w-3.5 h-3.5" />
          Сделка
        </Button>
        <Button
          variant="outline"
          size="sm"
          className="lg:hidden h-10 gap-1.5 border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2"
          onClick={onAddTransaction}
        >
          <Plus aria-hidden="true" className="w-3.5 h-3.5" />
          Сделка
        </Button>
      </div>
    </div>
  );
}
