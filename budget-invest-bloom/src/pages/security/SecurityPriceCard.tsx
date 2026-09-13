import { useState } from 'react';
import { AreaChart, Area, ReferenceDot, ReferenceLine, ResponsiveContainer, YAxis } from 'recharts';
import { useSecurityPriceHistory } from '@/hooks/useSecurityPriceHistory';
import { getPeriodDates, type Period } from '@/lib/periodDates';
import { formatSignedPercent, formatUnitPrice } from '@/lib/dateOptions';
import { CHART_TEXT_COLOR, formatDateTime, PORTFOLIO_CHART_COLOR, signClass } from '@/pages/investments/investmentsFormat';
import { SecurityLastPointDot } from '@/pages/security/SecurityLastPointDot';
import { SecurityPriceChart } from '@/pages/security/SecurityPriceChart';
import { averagePriceLabel, formatChartMonth, groupMarkers } from '@/pages/security/securityChartMarkers';
import { SECURITY_BUY_COLOR, SECURITY_SELL_COLOR, SIGN_TEXT_CLASS } from '@/pages/security/securityFormat';
import type { SecurityMarker, SecurityPagePrice } from '@/types/investment';

const PERIODS: { value: Period; label: string }[] = [
  { value: '1M', label: '1М' },
  { value: '3M', label: '3М' },
  { value: '1Y', label: '1Г' },
  { value: 'MAX', label: 'Всё' },
];

const PERIOD_PHRASE: Record<Period, string> = { '1M': 'месяц', '3M': '3 месяца', '1Y': 'год', MAX: 'всё время' };

// Phone header of the card (p.14): today's move next to the period's, or the stale notice
function MobileTodayChange({ price }: { price?: SecurityPagePrice }) {
  if (!price) return null;
  if (price.stale) return <span className="whitespace-nowrap text-app-warn">{`цены на ${formatDateTime(price.asOf)}`}</span>;
  if (price.dailyChangePercent == null) return null;
  return (
    <span className={`whitespace-nowrap ${SIGN_TEXT_CLASS[signClass(price.dailyChangePercent)]}`}>
      {formatSignedPercent(price.dailyChangePercent)} сегодня
    </span>
  );
}

interface SecurityPriceCardProps {
  ticker: string;
  price?: SecurityPagePrice;
  markers: SecurityMarker[];
  averagePrice: number | null;
}

export function SecurityPriceCard({ ticker, price, markers, averagePrice }: SecurityPriceCardProps) {
  // The phone has no period pills, so it always stays on the default year (p.14)
  const [period, setPeriod] = useState<Period>('1Y');
  const { from, to } = getPeriodDates(period);
  const { data, isLoading } = useSecurityPriceHistory(ticker, from, to);

  const series = data?.body?.series ?? [];
  const historyPending = data?.body?.historyPending ?? false;
  const chartData = series.map((point, index) => ({ index, date: point.date, close: point.close }));
  const lastIndex = chartData.length - 1;
  const markerGroups = groupMarkers(
    chartData.map((point) => point.date),
    markers,
  );

  const firstClose = series[0]?.close ?? null;
  const lastClose = series.length > 0 ? series[series.length - 1].close : null;
  const changePercent =
    firstClose !== null && firstClose !== 0 && lastClose !== null ? ((lastClose - firstClose) / firstClose) * 100 : null;
  const periodChange = changePercent !== null && (
    <span className={`font-mono font-semibold ${SIGN_TEXT_CLASS[signClass(changePercent)]}`}>{formatSignedPercent(changePercent)}</span>
  );

  return (
    <div className="glass-card p-3.5 lg:p-5 flex flex-col gap-1.5 lg:gap-2.5">
      <div className="hidden lg:flex items-center justify-between">
        <span className="font-display text-[22px] text-app-text">
          Цена{' '}
          <span className="font-sans text-app-text-muted text-[13px] ml-2">
            за {PERIOD_PHRASE[period]} {periodChange}
          </span>
        </span>
        <div className="flex gap-1">
          {PERIODS.map((p) => (
            <button
              key={p.value}
              type="button"
              aria-pressed={period === p.value}
              onClick={() => setPeriod(p.value)}
              className={`h-[26px] px-2.5 rounded-full font-mono text-xs ${
                period === p.value ? 'bg-app-surface-2 border border-app-border text-app-text' : 'text-app-text-muted hover:text-app-text'
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      <div className="lg:hidden flex items-baseline justify-between gap-3">
        <span className="font-mono text-[22px] font-semibold leading-none text-app-text">{price ? formatUnitPrice(price.current) : '—'}</span>
        <span className="font-mono text-xs text-right">
          <MobileTodayChange price={price} />
          {changePercent !== null && (
            <span className="whitespace-nowrap text-app-text-dim">
              {' · '}за {PERIOD_PHRASE[period]} {periodChange}
            </span>
          )}
        </span>
      </div>

      {isLoading || historyPending ? (
        <div className="h-20 lg:h-[220px] flex items-center justify-center text-app-text-dim text-sm">история цен ещё загружается</div>
      ) : chartData.length === 0 ? (
        <div className="h-20 lg:h-[220px] flex items-center justify-center text-app-text-dim text-sm">Нет данных за период</div>
      ) : (
        <>
          <div className="hidden lg:block">
            <SecurityPriceChart chartData={chartData} markerGroups={markerGroups} averagePrice={averagePrice} />
            <p className="text-app-text-dim text-[11px] mt-1">
              {averagePrice !== null ? 'точки — мои сделки, пунктир — средняя цена покупки' : 'точки — мои сделки'}
            </p>
          </div>

          <div className="lg:hidden">
            <ResponsiveContainer width="100%" height={80}>
              <AreaChart data={chartData} margin={{ top: 6, right: 6, left: 6, bottom: 6 }}>
                {/* Hidden, only to fit the line to its own range instead of recharts' default 0-based axis */}
                <YAxis hide domain={['dataMin', 'dataMax']} />
                {averagePrice !== null && (
                  <ReferenceLine y={averagePrice} ifOverflow="extendDomain" stroke={CHART_TEXT_COLOR} strokeWidth={1.2} strokeDasharray="5 4" />
                )}
                <Area
                  type="monotone"
                  dataKey="close"
                  stroke={PORTFOLIO_CHART_COLOR}
                  strokeWidth={2}
                  fill={PORTFOLIO_CHART_COLOR}
                  fillOpacity={0.08}
                  isAnimationActive={false}
                  dot={<SecurityLastPointDot lastIndex={lastIndex} />}
                />
                {markerGroups.map((group) => (
                  <ReferenceDot
                    key={`${group.index}-${group.kind}`}
                    x={group.index}
                    y={chartData[group.index].close}
                    r={4.5}
                    fill={group.kind === 'BUY' ? SECURITY_BUY_COLOR : SECURITY_SELL_COLOR}
                    stroke="#FFFFFF"
                    strokeWidth={2}
                  />
                ))}
              </AreaChart>
            </ResponsiveContainer>
            <div className="flex items-center justify-between gap-2 text-app-text-dim text-[10px] mt-1">
              <span className="shrink-0">{formatChartMonth(chartData[0]?.date)}</span>
              <span className="truncate">
                {averagePrice !== null ? `${averagePriceLabel(averagePrice)} · точки — сделки` : 'точки — сделки'}
              </span>
              <span className="shrink-0">{formatChartMonth(chartData[lastIndex]?.date)}</span>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
