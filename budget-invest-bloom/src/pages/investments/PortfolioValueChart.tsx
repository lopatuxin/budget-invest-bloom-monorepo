import { useState } from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { usePortfolioValueHistory } from '@/hooks/usePortfolioValueHistory';
import { getPeriodDates, type Period } from '@/lib/periodDates';
import { formatCompact, formatCurrency, formatSignedPercent } from '@/lib/dateOptions';
import { CHART_GRID_COLOR, CHART_TEXT_DIM_COLOR, PORTFOLIO_CHART_COLOR } from '@/pages/investments/investmentsFormat';

const PERIODS: { value: Period; label: string }[] = [
  { value: '1M', label: '1М' },
  { value: '3M', label: '3М' },
  { value: '1Y', label: '1Г' },
  { value: 'MAX', label: 'Всё' },
];

const PERIOD_PHRASE: Record<Period, string> = {
  '1M': 'месяц',
  '3M': '3 месяца',
  '1Y': 'год',
  MAX: 'всё время',
};

function xAxisLabel(dateStr: string, period: Period): string {
  const date = new Date(dateStr);
  return period === '1M'
    ? date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })
    : date.toLocaleDateString('ru-RU', { month: 'short', year: '2-digit' });
}

interface ValueTooltipEntry {
  value: number;
  payload?: { label: string };
}

function ValueTooltip({ active, payload }: { active?: boolean; payload?: ValueTooltipEntry[] }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-app-surface border border-app-border rounded-lg px-3 py-2.5 text-xs">
      <p className="text-app-text-dim mb-1">{payload[0].payload?.label}</p>
      <p className="text-app-text font-semibold font-mono">{formatCurrency(payload[0].value)}</p>
    </div>
  );
}

// Renders a plain (invisible) dot on every point except the last, which gets
// a ringed marker — matches the design's "only the last value is labelled" area chart.
function renderLastPointDot(lastIndex: number) {
  // recharts' Area `dot` render prop has no exported prop type for a custom function
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (props: any) => {
    const { cx, cy, index } = props;
    if (index !== lastIndex) return <g key={`dot-${index}`} />;
    return (
      <g key="portfolio-value-last-dot">
        <circle cx={cx} cy={cy} r={6} fill="#FFFFFF" />
        <circle cx={cx} cy={cy} r={4} fill={PORTFOLIO_CHART_COLOR} />
      </g>
    );
  };
}

interface PortfolioValueChartProps {
  variant: 'desktop' | 'mobile';
}

// Renders either the interactive desktop chart (period pills, Y axis, full grid)
// or the fixed-1Y mobile mini chart — PortfolioValueCard mounts one or the other
// via Tailwind visibility, same convention as OverviewCapitalChart.
export function PortfolioValueChart({ variant }: PortfolioValueChartProps) {
  const [period, setPeriod] = useState<Period>('1Y');
  const activePeriod = variant === 'desktop' ? period : '1Y';
  const { from, to } = getPeriodDates(activePeriod);
  const { data, isLoading } = usePortfolioValueHistory(from, to);

  const series = data?.body?.series ?? [];
  const historyPending = data?.body?.historyPending ?? false;
  const pricesStale = data?.body?.pricesStale ?? false;
  const chartData = series.map((point, index) => ({ ...point, index, label: xAxisLabel(point.date, activePeriod) }));
  const lastIndex = chartData.length - 1;

  const firstValue = series[0]?.value ?? null;
  const lastValue = series.length > 0 ? series[series.length - 1].value : null;
  const changePercent =
    firstValue !== null && firstValue !== 0 && lastValue !== null ? ((lastValue - firstValue) / firstValue) * 100 : null;

  const height = variant === 'desktop' ? 180 : 80;
  const desktopTickIndexes = Array.from(
    new Set([0, Math.floor(lastIndex / 4), Math.floor(lastIndex / 2), Math.floor((lastIndex * 3) / 4), lastIndex])
  );

  if (isLoading || chartData.length === 0) {
    return (
      <div style={{ height }} className="flex items-center justify-center text-app-text-dim text-xs">
        {isLoading ? 'Загрузка графика...' : 'Нет данных за период'}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1.5">
      {variant === 'desktop' && (
        <div className="flex items-center justify-between">
          <span className="text-app-text-muted text-[13px]">
            Стоимость за {PERIOD_PHRASE[period]}{' '}
            {changePercent !== null && (
              <span className={`font-mono font-semibold ${changePercent >= 0 ? 'text-app-good' : 'text-app-bad'}`}>
                {formatSignedPercent(changePercent)}
              </span>
            )}
          </span>
          <div className="flex gap-1">
            {PERIODS.map((p) => (
              <button
                key={p.value}
                type="button"
                onClick={() => setPeriod(p.value)}
                className={`h-[26px] px-2.5 rounded-full font-mono text-xs ${
                  period === p.value
                    ? 'bg-app-surface-2 border border-app-border text-app-text'
                    : 'text-app-text-muted hover:text-app-text'
                }`}
              >
                {p.label}
              </button>
            ))}
          </div>
        </div>
      )}
      <ResponsiveContainer width="100%" height={height}>
        <AreaChart
          data={chartData}
          margin={variant === 'desktop' ? { top: 8, right: 4, left: 0, bottom: 4 } : { top: 4, right: 0, left: 0, bottom: 0 }}
        >
          {variant === 'desktop' && <CartesianGrid stroke={CHART_GRID_COLOR} vertical={false} />}
          {variant === 'desktop' && (
            <YAxis
              tickCount={3}
              axisLine={false}
              tickLine={false}
              tick={{ fill: CHART_TEXT_DIM_COLOR, fontSize: 11 }}
              tickFormatter={formatCompact}
              width={56}
            />
          )}
          {variant === 'desktop' && (
            <XAxis
              dataKey="index"
              type="number"
              domain={[0, lastIndex]}
              axisLine={false}
              tickLine={false}
              interval={0}
              ticks={desktopTickIndexes}
              tickFormatter={(index: number) => chartData[index]?.label ?? ''}
              tick={{ fill: CHART_TEXT_DIM_COLOR, fontSize: 11 }}
              height={20}
            />
          )}
          <Tooltip content={<ValueTooltip />} />
          <Area
            type="monotone"
            dataKey="value"
            stroke={PORTFOLIO_CHART_COLOR}
            strokeWidth={2}
            fill={PORTFOLIO_CHART_COLOR}
            fillOpacity={0.1}
            isAnimationActive={false}
            dot={renderLastPointDot(lastIndex)}
          />
        </AreaChart>
      </ResponsiveContainer>
      {variant === 'mobile' && (
        <div className="flex items-center justify-between">
          <span className="text-app-text-dim text-[10px]">{chartData[0]?.label}</span>
          {changePercent !== null && <span className="text-app-text-dim text-[10px]">{formatSignedPercent(changePercent)} за год</span>}
        </div>
      )}
      {historyPending && (
        <p className="text-app-text-dim text-[11px]">история части бумаг ещё загружается, линия может измениться</p>
      )}
      {pricesStale && (
        <p className="text-app-text-dim text-[11px]">по части бумаг нет свежих цен, линия построена по последним известным</p>
      )}
    </div>
  );
}
