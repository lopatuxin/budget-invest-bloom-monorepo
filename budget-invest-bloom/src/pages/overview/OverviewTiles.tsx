import type { ReactNode } from 'react';
import { ArrowUpRight, ArrowDownRight } from 'lucide-react';
import { formatCurrency } from '@/lib/dateOptions';
import { formatDayMonth, formatSignedCurrency, formatSignedPercent, monthPrepositional, pluralSecurities } from '@/pages/overview/overviewFormat';
import type { NormStatus, OverviewPageResponse } from '@/types/budget';

// Same four-status palette as BudgetNormBadge's "income" variant, but this
// badge needs one decimal (portfolio.pnl.percent), while BudgetNormBadge
// always rounds to an integer — kept local since it is used only here.
const PNL_STYLES: Record<Exclude<NormStatus, 'NO_HISTORY'>, string> = {
  ABOVE_MUCH: 'bg-app-good-soft text-app-good',
  ABOVE: 'bg-app-good-soft text-app-good',
  NORMAL: 'bg-app-neutral-soft text-app-neutral',
  BELOW: 'bg-app-warn-soft text-app-warn',
};

function PortfolioPnlBadge({ percent, status }: { percent: number; status: NormStatus }) {
  if (status === 'NO_HISTORY') return null;
  const Arrow = percent >= 0 ? ArrowUpRight : ArrowDownRight;
  return (
    <span className={`inline-flex items-center gap-0.5 h-5 px-1.5 rounded-full font-mono text-xs whitespace-nowrap ${PNL_STYLES[status]}`}>
      <Arrow aria-hidden="true" className="w-3 h-3" />
      {formatSignedPercent(percent)}
    </span>
  );
}

interface OverviewTileProps {
  label: string;
  value: string;
  valueExtra?: ReactNode;
  subtitle: ReactNode;
}

function OverviewTile({ label, value, valueExtra, subtitle }: OverviewTileProps) {
  return (
    <div className="glass-card p-3 lg:p-4 flex flex-col gap-1 lg:gap-1.5">
      <span className="text-app-text-muted text-xs lg:text-[13px]">{label}</span>
      <div className="flex items-center gap-1.5 lg:gap-2">
        <span className="font-mono text-lg lg:text-[22px] font-semibold text-app-text">{value}</span>
        {valueExtra}
      </div>
      <span className="text-app-text-muted text-[11px] lg:text-xs">{subtitle}</span>
    </div>
  );
}

interface OverviewTilesProps {
  data: OverviewPageResponse;
  hasNoRecords: boolean;
}

export function OverviewTiles({ data, hasNoRecords }: OverviewTilesProps) {
  const { currentMonth, currentMonthBalance, portfolio, savings } = data;
  const monthLabel = monthPrepositional(currentMonth.month);

  const freeMoneySubtitle = hasNoRecords ? (
    'пока нет записей'
  ) : (
    <>
      все доходы минус все расходы ·{' '}
      <span className={currentMonthBalance >= 0 ? 'text-app-good' : 'text-app-bad'}>
        {formatSignedCurrency(currentMonthBalance)}
      </span>{' '}
      в {monthLabel}
    </>
  );

  const savingsSubtitle =
    savings.currentMonthRate == null
      ? 'в среднем за 12 месяцев'
      : `в среднем за 12 месяцев · в ${monthLabel} пока ${savings.currentMonthRate}%`;

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-2.5 lg:gap-4">
      <OverviewTile label="Свободные деньги" value={formatCurrency(data.capital.freeMoney)} subtitle={freeMoneySubtitle} />

      {!portfolio.available ? (
        <OverviewTile label="Портфель" value="—" subtitle="нет данных от биржи" />
      ) : portfolio.assetsCount === 0 ? (
        <OverviewTile label="Портфель" value={formatCurrency(0)} subtitle="бумаг пока нет" />
      ) : (
        <OverviewTile
          label="Портфель"
          value={formatCurrency(portfolio.value ?? 0)}
          valueExtra={
            portfolio.pnl?.percent != null ? <PortfolioPnlBadge percent={portfolio.pnl.percent} status={portfolio.pnl.status} /> : null
          }
          subtitle={`прибыль ${formatCurrency(portfolio.pnlAmount ?? 0)} · ${pluralSecurities(portfolio.assetsCount)}`}
        />
      )}

      <OverviewTile
        label="Норма сбережений"
        value={savings.rate12m == null ? '—' : `${savings.rate12m}%`}
        subtitle={savingsSubtitle}
      />

      {!portfolio.available ? (
        <OverviewTile label="Дивиденды за 12 месяцев" value="—" subtitle="нет данных от биржи" />
      ) : (
        <OverviewTile
          label="Дивиденды за 12 месяцев"
          value={formatCurrency(portfolio.dividends12m ?? 0)}
          subtitle={
            portfolio.nextDividend
              ? `ближайший — ${portfolio.nextDividend.securityName}, ${formatCurrency(portfolio.nextDividend.totalAmount)} ${formatDayMonth(portfolio.nextDividend.paymentDate)}`
              : 'ближайших выплат нет'
          }
        />
      )}
    </div>
  );
}
