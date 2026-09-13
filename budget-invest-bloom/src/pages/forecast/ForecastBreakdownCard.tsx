import { SecurityLogo } from '@/components/SecurityLogo';
import { formatSignedPercent } from '@/lib/dateOptions';
import { formatPercent } from '@/pages/investments/investmentsFormat';
import { describePayoutSubtitle } from '@/pages/forecast/forecastFormat';
import type { ProjectionBreakdownItem, ProjectionResult } from '@/types/investment';

// "Бумага" | "Доля" | "Рост цены" | "Выплаты" | "Итого" — the desktop table grid (p.15).
const GRID_TEMPLATE_COLUMNS = 'minmax(0,1.6fr) 90px 110px 130px 100px';

interface BreakdownRowProps {
  item: ProjectionBreakdownItem;
}

function BreakdownRow({ item }: BreakdownRowProps) {
  const payout = describePayoutSubtitle(item);
  const totalPercent = item.priceGrowthPercent + item.payoutYieldPercent;

  return (
    <div className="grid items-center gap-2 py-2.5 border-b border-app-border" style={{ gridTemplateColumns: GRID_TEMPLATE_COLUMNS }}>
      <div className="flex items-center gap-2.5 min-w-0">
        <SecurityLogo ticker={item.ticker} size={28} securityType={item.securityType} />
        <div className="min-w-0">
          <div className="font-mono text-sm font-semibold text-app-text truncate">{item.ticker}</div>
          <div className="text-[12px] text-app-text-muted truncate">
            {item.securityName}
            {item.historyPending && <span className="text-app-text-dim"> · история загружается</span>}
          </div>
        </div>
      </div>
      <span className="font-mono text-sm text-app-text text-right">{formatPercent(item.weightPercent)}</span>
      <span className="font-mono text-sm text-app-text text-right">{formatSignedPercent(item.priceGrowthPercent)}</span>
      <div className="text-right">
        <div className="font-mono text-sm text-app-text">{formatPercent(item.payoutYieldPercent)}</div>
        <div className={`text-[11px] ${payout.isWarn ? 'text-app-warn' : 'text-app-text-dim'}`}>{payout.text}</div>
      </div>
      <span className="font-mono text-sm font-semibold text-app-text text-right">{formatSignedPercent(totalPercent)}</span>
    </div>
  );
}

interface MobileBreakdownRowProps {
  item: ProjectionBreakdownItem;
}

function MobileBreakdownRow({ item }: MobileBreakdownRowProps) {
  const totalPercent = item.priceGrowthPercent + item.payoutYieldPercent;
  const payout = describePayoutSubtitle(item);
  return (
    <div className="py-2.5">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <SecurityLogo ticker={item.ticker} size={24} securityType={item.securityType} />
          <span className="font-mono text-sm font-semibold text-app-text truncate">{item.ticker}</span>
          <span className="text-app-text-dim text-xs shrink-0">{formatPercent(item.weightPercent)}</span>
        </div>
        <span className="font-mono text-sm font-semibold text-app-text shrink-0">{formatSignedPercent(totalPercent)}</span>
      </div>
      <p className="text-app-text-dim text-[11px] mt-0.5">
        рост {formatSignedPercent(item.priceGrowthPercent)} · выплаты {formatPercent(item.payoutYieldPercent)}
        {item.historyPending && ' · история загружается'}
        {!item.historyPending && payout.isWarn && <span className="text-app-warn"> · {payout.text}</span>}
      </p>
    </div>
  );
}

interface ForecastBreakdownCardProps {
  result: ProjectionResult;
}

// "Из чего сложилась доходность" — per-security split of the portfolio's weighted annual
// return into price growth and after-tax payouts (p.15); the desktop table's own totals row
// ("Портфель") reuses the result's already-weighted priceGrowthPercent/payoutYieldPercent.
export function ForecastBreakdownCard({ result }: ForecastBreakdownCardProps) {
  const portfolioTotalPercent = result.priceGrowthPercent + result.payoutYieldPercent;

  return (
    <div className="glass-card p-5 flex flex-col gap-1">
      <span className="font-display text-[22px] text-app-text">Из чего сложилась доходность</span>

      <div className="hidden lg:block mt-2">
        <div
          className="grid gap-2 pb-2 border-b border-app-border-strong text-app-text-dim text-xs"
          style={{ gridTemplateColumns: GRID_TEMPLATE_COLUMNS }}
        >
          <span>Бумага</span>
          <span className="text-right">Доля</span>
          <span className="text-right">Рост цены</span>
          <span className="text-right">Выплаты</span>
          <span className="text-right">Итого</span>
        </div>

        {result.breakdown.map((item) => (
          <BreakdownRow key={item.ticker} item={item} />
        ))}

        <div className="grid items-center gap-2 pt-2.5" style={{ gridTemplateColumns: GRID_TEMPLATE_COLUMNS }}>
          <span className="font-medium text-app-text">Портфель</span>
          <span className="font-mono text-sm text-app-text text-right">{formatPercent(100)}</span>
          <span className="font-mono text-sm text-app-text text-right">{formatSignedPercent(result.priceGrowthPercent)}</span>
          <span className="font-mono text-sm text-app-text text-right">{formatPercent(result.payoutYieldPercent)}</span>
          <span className="font-mono text-sm font-semibold text-app-text text-right">{formatSignedPercent(portfolioTotalPercent)}</span>
        </div>
      </div>

      <div className="lg:hidden divide-y divide-app-border">
        {result.breakdown.map((item) => (
          <MobileBreakdownRow key={item.ticker} item={item} />
        ))}
        <div className="pt-2.5">
          <div className="flex items-center justify-between gap-2">
            <span className="font-medium text-app-text">Портфель</span>
            <span className="font-mono text-sm font-semibold text-app-text shrink-0">{formatSignedPercent(portfolioTotalPercent)}</span>
          </div>
          <p className="text-app-text-dim text-[11px] mt-0.5">
            рост {formatSignedPercent(result.priceGrowthPercent)} · выплаты {formatPercent(result.payoutYieldPercent)}
          </p>
        </div>
      </div>

      <p className="text-app-text-dim text-[11px] mt-3">
        рост цены — среднегодовой за последние 10 лет; выплаты — средние за 5 полных лет после НДФЛ; бумаги с незавершённой загрузкой
        истории помечены.
      </p>
    </div>
  );
}
