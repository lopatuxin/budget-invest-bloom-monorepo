import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { formatCompact, formatCurrency } from '@/lib/dateOptions';
import { CHART_GRID_COLOR, CHART_TEXT_DIM_COLOR, PORTFOLIO_CHART_COLOR } from '@/pages/investments/investmentsFormat';
import type { ProjectionPoint } from '@/types/investment';

// "Внесено" dashed line — the one color from docs/plans/security-page-redesign.md
// "UI" not already exported by an existing format module.
const CONTRIBUTED_COLOR = '#CFC9BD';

interface ForecastTooltipEntry {
  payload?: ProjectionPoint;
}

function ForecastTooltip({ active, payload }: { active?: boolean; payload?: ForecastTooltipEntry[] }) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  if (!point) return null;
  return (
    <div className="bg-app-surface border border-app-border rounded-lg px-3 py-2.5 text-xs space-y-1">
      <p className="text-app-text-dim">{new Date(point.date).toLocaleDateString('ru-RU', { month: 'long', year: 'numeric' })}</p>
      <p className="text-app-text font-semibold font-mono">{formatCurrency(point.value)}</p>
      <p className="text-app-text-muted font-mono">внесено {formatCurrency(point.contributed)}</p>
      {point.deposit > 0 && <p className="text-app-good font-mono">пополнение {formatCurrency(point.deposit)}</p>}
      {point.withdrawal > 0 && <p className="text-app-bad font-mono">изъятие {formatCurrency(point.withdrawal)}</p>}
    </div>
  );
}

interface ForecastChartProps {
  series: ProjectionPoint[];
  pendingHistoryTickers: string[];
}

export function ForecastChart({ series, pendingHistoryTickers }: ForecastChartProps) {
  const chartData = series.map((point, index) => ({ ...point, index }));
  const lastIndex = chartData.length - 1;
  const tickIndexes =
    lastIndex > 0
      ? Array.from(new Set([0, Math.floor(lastIndex / 4), Math.floor(lastIndex / 2), Math.floor((lastIndex * 3) / 4), lastIndex]))
      : [0];

  return (
    <div className="glass-card p-5 flex flex-col gap-2.5">
      <div className="flex items-center justify-between">
        <span className="font-display text-[22px] text-app-text">Как растёт портфель</span>
        <div className="flex gap-4 text-app-text-muted text-xs">
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-2.5 h-2.5 rounded-sm" style={{ backgroundColor: PORTFOLIO_CHART_COLOR }} />
            стоимость
          </span>
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-3.5 h-0 border-t-2 border-dashed" style={{ borderColor: CONTRIBUTED_COLOR }} />
            внесено
          </span>
        </div>
      </div>
      <ResponsiveContainer width="100%" height={280}>
        <AreaChart data={chartData} margin={{ top: 8, right: 4, left: 0, bottom: 4 }}>
          <CartesianGrid stroke={CHART_GRID_COLOR} vertical={false} />
          <YAxis axisLine={false} tickLine={false} tick={{ fill: CHART_TEXT_DIM_COLOR, fontSize: 11 }} tickFormatter={formatCompact} width={64} />
          <XAxis
            dataKey="index"
            type="number"
            domain={[0, lastIndex]}
            axisLine={false}
            tickLine={false}
            interval={0}
            ticks={tickIndexes}
            tickFormatter={(index: number) => (chartData[index] ? new Date(chartData[index].date).getFullYear().toString() : '')}
            tick={{ fill: CHART_TEXT_DIM_COLOR, fontSize: 11 }}
            height={20}
          />
          <Tooltip content={<ForecastTooltip />} />
          <Area type="monotone" dataKey="contributed" stroke={CONTRIBUTED_COLOR} strokeWidth={2} strokeDasharray="5 4" fill="none" isAnimationActive={false} />
          <Area
            type="monotone"
            dataKey="value"
            stroke={PORTFOLIO_CHART_COLOR}
            strokeWidth={2}
            fill={PORTFOLIO_CHART_COLOR}
            fillOpacity={0.08}
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
      <p className="text-app-text-dim text-[11px]">
        доходность — средняя годовая по истории цен и дивидендов каждой бумаги, взвешенная по её доле; это не предсказание, а
        арифметика: так вырастет портфель, если прошлое повторится.
        {pendingHistoryTickers.length > 0 &&
          ` история части бумаг (${pendingHistoryTickers.join(', ')}) ещё загружается, прогноз приблизительный.`}
      </p>
    </div>
  );
}
