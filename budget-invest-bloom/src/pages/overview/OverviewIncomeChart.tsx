import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Cell, ResponsiveContainer } from 'recharts';
import { formatCurrency } from '@/lib/dateOptions';
import { capitalize, formatThousands, monthNominative, monthShortLabel, OVERVIEW_CHART_COLORS } from '@/pages/overview/overviewFormat';
import type { MonthTotals } from '@/types/budget';

type IncomeChartPoint = MonthTotals & { label: string; spent: number; savedPositive: number };

interface IncomeTooltipEntry {
  value: number;
  // recharts passes the full data record in payload; keep it generic
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  payload?: any;
}

function IncomeTooltip({ active, payload }: { active?: boolean; payload?: IncomeTooltipEntry[] }) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload as IncomeChartPoint;
  return (
    <div className="bg-app-surface border border-app-border rounded-lg px-3 py-2.5 text-xs">
      <p className="text-app-text-dim mb-1.5">{point.label}</p>
      <p className="text-app-text">доход <span className="font-mono">{formatCurrency(point.income)}</span></p>
      <p className="text-app-text">расходы <span className="font-mono">{formatCurrency(point.expenses)}</span></p>
      <p className="text-app-text font-semibold mt-1">сбережено <span className="font-mono">{formatCurrency(point.saved)}</span></p>
    </div>
  );
}

function IncomeBars({ data, isDesktop }: { data: IncomeChartPoint[]; isDesktop: boolean }) {
  return (
    <ResponsiveContainer width="100%" height={isDesktop ? 220 : 160}>
      <BarChart data={data} margin={{ top: 8, right: 0, left: 0, bottom: 4 }}>
        <CartesianGrid stroke={OVERVIEW_CHART_COLORS.border} vertical={false} />
        <XAxis
          dataKey="month"
          axisLine={false}
          tickLine={false}
          interval={isDesktop ? 0 : 1}
          tick={{ fill: OVERVIEW_CHART_COLORS.textDim, fontSize: isDesktop ? 11 : 10 }}
          tickFormatter={(value: number) => monthShortLabel(value)}
        />
        <YAxis
          hide={!isDesktop}
          axisLine={false}
          tickLine={false}
          tick={{ fill: OVERVIEW_CHART_COLORS.textDim, fontSize: 11 }}
          tickFormatter={formatThousands}
          width={44}
        />
        <Tooltip content={<IncomeTooltip />} cursor={false} />
        <Bar dataKey="spent" stackId="month" fill={OVERVIEW_CHART_COLORS.expense} barSize={34} isAnimationActive={false}>
          {data.map((entry, index) => (
            <Cell key={`spent-${index}`} fillOpacity={entry.partial ? 0.45 : 1} />
          ))}
        </Bar>
        <Bar
          dataKey="savedPositive"
          stackId="month"
          fill={OVERVIEW_CHART_COLORS.income}
          radius={[3, 3, 0, 0]}
          stroke="#FFFFFF"
          strokeWidth={2}
          barSize={34}
          isAnimationActive={false}
        >
          {data.map((entry, index) => (
            <Cell key={`saved-${index}`} fillOpacity={entry.partial ? 0.45 : 1} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

interface OverviewIncomeChartProps {
  months: MonthTotals[];
}

export function OverviewIncomeChart({ months }: OverviewIncomeChartProps) {
  const isEmpty = months.every((month) => month.income === 0 && month.expenses === 0);
  const currentMonthEntry = months[months.length - 1] as MonthTotals | undefined;

  const data: IncomeChartPoint[] = months.map((month) => ({
    ...month,
    label: `${monthShortLabel(month.month)} ${month.year}`,
    spent: month.expenses,
    savedPositive: Math.max(month.saved, 0),
  }));

  return (
    <div className="glass-card p-4 lg:p-5 flex flex-col gap-3 h-full">
      <div className="flex items-center justify-between">
        <h2 className="font-display text-[20px] lg:text-[22px] text-app-text">Доходы по месяцам</h2>
        <div className="flex items-center gap-3 lg:gap-4 text-app-text-muted text-xs">
          <span className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-[3px] inline-block" style={{ background: OVERVIEW_CHART_COLORS.expense }} />
            потрачено
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-[3px] inline-block" style={{ background: OVERVIEW_CHART_COLORS.income }} />
            сбережено
          </span>
        </div>
      </div>

      {isEmpty ? (
        <div className="h-[160px] lg:h-[220px] flex items-center justify-center text-app-text-muted text-sm">
          Данных за 12 месяцев пока нет
        </div>
      ) : (
        <>
          <div className="hidden lg:block">
            <IncomeBars data={data} isDesktop />
          </div>
          <div className="lg:hidden">
            <IncomeBars data={data} isDesktop={false} />
          </div>
          {currentMonthEntry?.partial && (
            <span className="text-app-text-dim text-[11px]">
              {capitalize(monthNominative(currentMonthEntry.month))} ещё не закончился и показан бледнее
            </span>
          )}
        </>
      )}
    </div>
  );
}
