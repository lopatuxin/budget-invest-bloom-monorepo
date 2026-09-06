import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { formatCurrency } from '@/lib/dateOptions';
import { formatCompact, monthShortLabel, OVERVIEW_CHART_COLORS } from '@/pages/overview/overviewFormat';
import type { CapitalPoint } from '@/types/budget';

type CapitalChartPoint = CapitalPoint & { label: string };

interface CapitalTooltipEntry {
  value: number;
  // recharts passes the full data record in payload; keep it generic
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  payload?: any;
}

function CapitalTooltip({ active, payload }: { active?: boolean; payload?: CapitalTooltipEntry[] }) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload as CapitalChartPoint;
  return (
    <div className="bg-app-surface border border-app-border rounded-lg px-3 py-2.5 text-xs">
      <p className="text-app-text-dim mb-1.5">{point.label}</p>
      <p className="text-app-text">своб. деньги <span className="font-mono">{formatCurrency(point.freeMoney)}</span></p>
      <p className="text-app-text">портфель <span className="font-mono">{formatCurrency(point.portfolioValue)}</span></p>
      <p className="text-app-text font-semibold mt-1">капитал <span className="font-mono">{formatCurrency(point.total)}</span></p>
    </div>
  );
}

// Renders a plain circle marker on every point except the last, which gets a
// larger ringed dot plus its value written above it — matches the design's
// "only the last value is labelled" area chart.
function renderLastPointDot(lastIndex: number) {
  // recharts' Area `dot` render prop has no exported prop type for a custom function
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (props: any) => {
    const { cx, cy, index, payload } = props;
    if (index !== lastIndex) return <g key={`dot-${index}`} />;
    return (
      <g key="capital-last-dot">
        <circle cx={cx} cy={cy} r={6} fill="#FFFFFF" />
        <circle cx={cx} cy={cy} r={4} fill={OVERVIEW_CHART_COLORS.income} />
        <text x={cx} y={cy - 12} textAnchor="end" fontSize={13} fontWeight={600} fill={OVERVIEW_CHART_COLORS.text}>
          {formatCurrency(payload.total)}
        </text>
      </g>
    );
  };
}

function CapitalArea({ data, isDesktop }: { data: CapitalChartPoint[]; isDesktop: boolean }) {
  const lastIndex = data.length - 1;

  return (
    <ResponsiveContainer width="100%" height={isDesktop ? 200 : 90}>
      <AreaChart data={data} margin={isDesktop ? { top: 24, right: 8, left: 0, bottom: 4 } : { top: 18, right: 0, left: 0, bottom: 4 }}>
        {isDesktop && <CartesianGrid stroke={OVERVIEW_CHART_COLORS.border} vertical={false} />}
        {isDesktop && (
          <YAxis
            tickCount={3}
            axisLine={false}
            tickLine={false}
            tick={{ fill: OVERVIEW_CHART_COLORS.textDim, fontSize: 11 }}
            tickFormatter={formatCompact}
            width={56}
          />
        )}
        <XAxis
          dataKey="label"
          axisLine={false}
          tickLine={false}
          interval={isDesktop ? 0 : undefined}
          ticks={isDesktop ? undefined : [data[0]?.label, data[lastIndex]?.label]}
          tick={
            isDesktop
              ? { fill: OVERVIEW_CHART_COLORS.textDim, fontSize: 11 }
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              : (tickProps: any) => (
                  <text
                    x={tickProps.x}
                    y={tickProps.y + 10}
                    textAnchor={tickProps.payload.value === data[0]?.label ? 'start' : 'end'}
                    fontSize={10}
                    fill={OVERVIEW_CHART_COLORS.textDim}
                  >
                    {tickProps.payload.value}
                  </text>
                )
          }
          tickFormatter={isDesktop ? (value: string) => value.split(' ')[0] : undefined}
          height={isDesktop ? 20 : 16}
        />
        <Tooltip content={<CapitalTooltip />} />
        <Area
          type="monotone"
          dataKey="total"
          stroke={OVERVIEW_CHART_COLORS.income}
          strokeWidth={2}
          fill={OVERVIEW_CHART_COLORS.income}
          fillOpacity={0.1}
          isAnimationActive={false}
          dot={renderLastPointDot(lastIndex)}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}

interface OverviewCapitalChartProps {
  history: CapitalPoint[];
}

// Renders both the desktop (200px, full grid + Y axis) and mobile (90px,
// first/last label only) variants — toggled with Tailwind visibility so the
// page layout stays CSS-driven, same as the rest of the app-shell.
export function OverviewCapitalChart({ history }: OverviewCapitalChartProps) {
  const data: CapitalChartPoint[] = history.map((point) => ({
    ...point,
    label: `${monthShortLabel(point.month)} ${point.year}`,
  }));

  return (
    <>
      <div className="hidden lg:block">
        <CapitalArea data={data} isDesktop />
      </div>
      <div className="lg:hidden">
        <CapitalArea data={data} isDesktop={false} />
      </div>
    </>
  );
}
