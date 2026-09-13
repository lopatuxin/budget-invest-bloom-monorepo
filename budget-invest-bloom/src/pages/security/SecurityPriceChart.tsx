import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ReferenceDot, ReferenceLine, ResponsiveContainer, Customized } from 'recharts';
import { formatUnitPrice, parseApiDate } from '@/lib/dateOptions';
import { formatThousands } from '@/pages/overview/overviewFormat';
import { CHART_GRID_COLOR, CHART_TEXT_COLOR, CHART_TEXT_DIM_COLOR, PORTFOLIO_CHART_COLOR } from '@/pages/investments/investmentsFormat';
import { SecurityChartLabels } from '@/pages/security/SecurityChartLabels';
import { SecurityLastPointDot } from '@/pages/security/SecurityLastPointDot';
import { formatChartMonth, type MarkerGroup, type SecurityChartPoint } from '@/pages/security/securityChartMarkers';
import { SECURITY_BUY_COLOR, SECURITY_SELL_COLOR } from '@/pages/security/securityFormat';

interface PriceTooltipEntry {
  value: number;
  payload?: { date: string };
}

function PriceTooltip({ active, payload }: { active?: boolean; payload?: PriceTooltipEntry[] }) {
  if (!active || !payload?.length) return null;
  const date = payload[0].payload?.date;
  return (
    <div className="bg-app-surface border border-app-border rounded-lg px-3 py-2.5 text-xs">
      {date && (
        <p className="text-app-text-dim mb-1">
          {parseApiDate(date).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short', year: 'numeric' })}
        </p>
      )}
      <p className="text-app-text font-semibold font-mono">{formatUnitPrice(payload[0].value)}</p>
    </div>
  );
}

interface SecurityPriceChartProps {
  chartData: SecurityChartPoint[];
  markerGroups: MarkerGroup[];
  averagePrice: number | null;
}

// Desktop price chart (p.10): axes, tooltip, the dashed average and trade dots, with every
// caption drawn by SecurityChartLabels so they can avoid each other.
export function SecurityPriceChart({ chartData, markerGroups, averagePrice }: SecurityPriceChartProps) {
  const lastIndex = chartData.length - 1;
  const tickIndexes =
    lastIndex > 0
      ? Array.from(new Set([0, Math.floor(lastIndex / 4), Math.floor(lastIndex / 2), Math.floor((lastIndex * 3) / 4), lastIndex]))
      : [0];

  return (
    <ResponsiveContainer width="100%" height={220}>
      <AreaChart data={chartData} margin={{ top: 8, right: 4, left: 0, bottom: 4 }}>
        <CartesianGrid stroke={CHART_GRID_COLOR} vertical={false} />
        {/* recharts starts a numeric axis at 0 by default, which flattens a price line into a strip at the top */}
        <YAxis
          domain={['auto', 'auto']}
          axisLine={false}
          tickLine={false}
          tick={{ fill: CHART_TEXT_DIM_COLOR, fontSize: 11 }}
          tickFormatter={formatThousands}
          width={56}
        />
        <XAxis
          dataKey="index"
          type="number"
          domain={[0, lastIndex]}
          axisLine={false}
          tickLine={false}
          interval={0}
          ticks={tickIndexes}
          tickFormatter={(index: number) => formatChartMonth(chartData[index]?.date)}
          tick={{ fill: CHART_TEXT_DIM_COLOR, fontSize: 11 }}
          height={20}
        />
        <Tooltip content={<PriceTooltip />} />
        {averagePrice !== null && (
          // extendDomain: a period whose prices never come near the average still shows the line
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
            r={5}
            fill={group.kind === 'BUY' ? SECURITY_BUY_COLOR : SECURITY_SELL_COLOR}
            stroke="#FFFFFF"
            strokeWidth={2}
          />
        ))}
        <Customized
          component={
            <SecurityChartLabels markerGroups={markerGroups} closes={chartData.map((point) => point.close)} averagePrice={averagePrice} />
          }
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
