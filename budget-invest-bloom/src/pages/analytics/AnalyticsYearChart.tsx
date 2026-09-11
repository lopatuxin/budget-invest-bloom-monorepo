import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Cell, ResponsiveContainer } from 'recharts';
import { CHART_GRID_COLOR, formatCurrency } from '@/lib/dateOptions';
import { capitalize, monthNominative, monthShortLabel } from '@/lib/monthNames';
import { ANALYTICS_CHART_COLORS, formatSignedPercentDiff, type AnalyticsTab } from '@/pages/analytics/analyticsFormat';
import { formatThousands } from '@/pages/overview/overviewFormat';
import type { AnalyticsMonthPoint } from '@/types/budget';

type ChartPoint = { month: number; current: number | null; previous: number | null; partial: boolean };

interface ChartTooltipEntry {
  value: number;
  // recharts passes the full data record in payload; keep it generic
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  payload?: any;
}

function amountLabel(tab: AnalyticsTab, amount: number): string {
  return tab === 'savings' ? `сбережено ${formatCurrency(amount)}` : formatCurrency(amount);
}

function ChartTooltip({ active, payload, tab, year, previousYear }: {
  active?: boolean;
  payload?: ChartTooltipEntry[];
  tab: AnalyticsTab;
  year: number;
  previousYear: number;
}) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload as ChartPoint;
  const hasBoth = point.current != null && point.previous != null;

  return (
    <div className="bg-app-surface border border-app-border rounded-lg px-3 py-2.5 text-xs">
      <p className="text-app-text-dim mb-1.5">{capitalize(monthNominative(point.month))} {year}</p>
      {point.current != null && <p className="text-app-text">{year} — <span className="font-mono">{amountLabel(tab, point.current)}</span></p>}
      {point.previous != null && <p className="text-app-text">{previousYear} — <span className="font-mono">{amountLabel(tab, point.previous)}</span></p>}
      {hasBoth && (
        <p className="text-app-text font-semibold mt-1">
          разница <span className="font-mono">{formatSignedPercentDiff(point.current as number, point.previous as number)}</span>
        </p>
      )}
    </div>
  );
}

function ChartBars({ data, isDesktop, tab, year, previousYear, showPrevious }: {
  data: ChartPoint[];
  isDesktop: boolean;
  tab: AnalyticsTab;
  year: number;
  previousYear: number;
  showPrevious: boolean;
}) {
  return (
    <ResponsiveContainer width="100%" height={isDesktop ? 230 : 160}>
      <BarChart data={data} barGap={4} margin={{ top: 8, right: 0, left: 0, bottom: 4 }}>
        <CartesianGrid stroke={CHART_GRID_COLOR} vertical={false} />
        <XAxis
          dataKey="month"
          axisLine={false}
          tickLine={false}
          interval={isDesktop ? 0 : 2}
          tick={{ fill: '#9AA0A8', fontSize: isDesktop ? 11 : 10 }}
          tickFormatter={(value: number) => monthShortLabel(value)}
        />
        <YAxis
          hide={!isDesktop}
          axisLine={false}
          tickLine={false}
          tick={{ fill: '#9AA0A8', fontSize: 11 }}
          tickFormatter={formatThousands}
          width={56}
        />
        <Tooltip content={<ChartTooltip tab={tab} year={year} previousYear={previousYear} />} cursor={false} />
        {showPrevious && (
          <Bar dataKey="previous" fill={ANALYTICS_CHART_COLORS.previous} radius={[3, 3, 0, 0]} barSize={isDesktop ? 30 : 8} isAnimationActive={false} />
        )}
        <Bar dataKey="current" fill={ANALYTICS_CHART_COLORS.current} radius={[3, 3, 0, 0]} barSize={isDesktop ? 30 : 8} isAnimationActive={false}>
          {data.map((entry, index) => (
            <Cell key={index} fillOpacity={entry.partial ? 0.45 : 1} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

interface AnalyticsYearChartProps {
  tab: AnalyticsTab;
  year: number;
  previousYear: number;
  previousYearHasData: boolean;
  months: AnalyticsMonthPoint[];
}

export function AnalyticsYearChart({ tab, year, previousYear, previousYearHasData, months }: AnalyticsYearChartProps) {
  const data: ChartPoint[] = months.map((m) => ({
    month: m.month,
    current: m.current ?? null,
    previous: previousYearHasData ? m.previous : null,
    partial: m.partial,
  }));
  const partialMonth = months.find((m) => m.partial);

  return (
    <div className="glass-card p-4 lg:p-5 flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="font-display text-[20px] lg:text-[22px] text-app-text">По месяцам: {year} против {previousYear}</h2>
        <div className="flex items-center gap-3 lg:gap-4 text-app-text-muted text-xs">
          <span className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-[3px] inline-block" style={{ background: ANALYTICS_CHART_COLORS.current }} />
            {year}
          </span>
          {previousYearHasData && (
            <span className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-[3px] inline-block" style={{ background: ANALYTICS_CHART_COLORS.previous }} />
              {previousYear}
            </span>
          )}
        </div>
      </div>

      <div className="hidden lg:block">
        <ChartBars data={data} isDesktop tab={tab} year={year} previousYear={previousYear} showPrevious={previousYearHasData} />
      </div>
      <div className="lg:hidden">
        <ChartBars data={data} isDesktop={false} tab={tab} year={year} previousYear={previousYear} showPrevious={previousYearHasData} />
      </div>

      {partialMonth && (
        <span className="text-app-text-dim text-[11px]">
          {capitalize(monthNominative(partialMonth.month))} ещё не закончился и показан бледнее; в средних и в «самом дорогом месяце» его нет
        </span>
      )}
    </div>
  );
}
