import { Link } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatCompact, formatCurrency } from '@/lib/dateOptions';
import { NAV_ITEMS } from '@/lib/nav';
import { CATEGORY_CHART_COLORS, chartCaption, hasNormHistory, monthAxisLabel, normLineLabel } from '@/pages/category/categoryFormat';
import type { CategoryMonthAmount, NormComparison } from '@/types/budget';

type ChartPoint = CategoryMonthAmount & { label: string };

interface MonthTooltipEntry {
  payload?: ChartPoint;
}

function MonthTooltip({ active, payload, averageMonthly }: { active?: boolean; payload?: MonthTooltipEntry[]; averageMonthly: number | null }) {
  if (!active || !payload?.length || !payload[0].payload) return null;
  const point = payload[0].payload;
  return (
    <div className="bg-app-surface border border-app-border rounded-lg px-3 py-2.5 text-xs">
      <p className="text-app-text-dim mb-1.5">{point.label}</p>
      <p className="text-app-text font-mono">{formatCurrency(point.amount)}</p>
      {averageMonthly != null && <p className="text-app-text-muted mt-1">обычно за месяц {formatCurrency(averageMonthly)}</p>}
    </div>
  );
}

// Single source of truth for the norm-line label's size — used for the canvas measurement font
// string, the rendered <text>'s fontSize, and (via NORM_LABEL_HALF_HEIGHT) the vertical clamp
// below. Three places used to hardcode 11 independently; changing one silently broke the other two.
const NORM_LABEL_FONT_SIZE = 11;
const NORM_LABEL_FONT = `500 ${NORM_LABEL_FONT_SIZE}px "IBM Plex Sans", sans-serif`;
// Half the label's own rendered line box — IBM Plex Sans lays out at roughly 1.3x the font
// size — so the vertical clamp in NormLabel tracks NORM_LABEL_FONT_SIZE instead of a bare
// number that would drift from it.
const NORM_LABEL_HALF_HEIGHT = Math.round((NORM_LABEL_FONT_SIZE * 1.3) / 2);
const NORM_LABEL_GAP = 10;

let measurementCanvas: HTMLCanvasElement | null = null;

// IBM Plex Sans 500 is loaded via @fontsource (main.tsx), so this measures with the same
// weight the chart's own <text> renders with — an actual pixel width instead of a guessed
// per-glyph average that a wide amount can outgrow (margin.right was sized that way twice
// before and got clipped both times). NORM_LABEL_GAP is doubled into the margin below as a
// buffer, in case this particular canvas measures before the font finishes loading and falls
// back to the browser's default sans-serif for a moment.
function measureTextWidth(text: string, font: string): number {
  if (!measurementCanvas) measurementCanvas = document.createElement('canvas');
  const context = measurementCanvas.getContext('2d');
  if (!context) return text.length * 8;
  context.font = font;
  return context.measureText(text).width;
}

interface NormLabelViewBox {
  x: number;
  y: number;
  width: number;
}

interface NormLabelProps {
  viewBox?: NormLabelViewBox;
  text: string;
  chartHeight: number;
}

// margin.right (set in MonthsBars, sized to this exact text by measureTextWidth) reserves
// enough room in the chart's own right gutter for the label, so it lives entirely outside the
// plot: it can never overlap a bar, and it can never be clipped horizontally. The one
// remaining risk is vertical — ifOverflow="extendDomain" can push the norm line to the very
// top of the plot, where a label centered on it would clip against the SVG's own top edge —
// so its vertical center is clamped to stay inside the chart's height.
function NormLabel({ viewBox, text, chartHeight }: NormLabelProps) {
  if (!viewBox) return null;
  const halfHeight = NORM_LABEL_HALF_HEIGHT;
  // dominant-baseline="middle" centers on the font's own metric, not this string's actual ink —
  // "обычно 120 000" has no descenders, so its ink sits visibly above the nominal center and
  // pokes ~1px past a bare half-height; measured on the "norm above every bar" case, where
  // the line sits right at the plot's top edge. This buffer covers that plus real room to spare.
  const edgeBuffer = 4;
  const centerY = Math.min(Math.max(viewBox.y, halfHeight + edgeBuffer), chartHeight - halfHeight - edgeBuffer);
  return (
    <text
      x={viewBox.x + viewBox.width + NORM_LABEL_GAP}
      y={centerY}
      dominantBaseline="middle"
      fontSize={NORM_LABEL_FONT_SIZE}
      fontWeight={500}
      fill={CATEGORY_CHART_COLORS.text}
    >
      {text}
    </text>
  );
}

interface MonthsBarsProps {
  data: ChartPoint[];
  averageMonthly: number | null;
  height: number;
  barSize: number;
  isDesktop: boolean;
}

// Every third month's label on mobile, plus the last one, so the axis reads "окт, янв, апр, июл, сен"
// instead of recharts' own overlap-avoidance dropping an arbitrary month from the middle.
function mobileTicks(data: ChartPoint[]): string[] {
  return data.filter((_, index) => index % 3 === 0 || index === data.length - 1).map((point) => point.label);
}

function MonthsBars({ data, averageMonthly, height, barSize, isDesktop }: MonthsBarsProps) {
  // The label only renders on desktop (the phone chart has no room for it — see the caption
  // line under the chart instead), so the mobile chart keeps margin.right at 0.
  const normLabelText = isDesktop && averageMonthly != null ? normLineLabel(averageMonthly) : null;
  const marginRight = normLabelText ? Math.ceil(measureTextWidth(normLabelText, NORM_LABEL_FONT)) + NORM_LABEL_GAP * 2 : 0;

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: marginRight, left: 0, bottom: 4 }}>
        <CartesianGrid stroke={CATEGORY_CHART_COLORS.border} vertical={false} />
        <XAxis
          dataKey="label"
          axisLine={false}
          tickLine={false}
          {...(isDesktop ? { interval: 0 } : { ticks: mobileTicks(data) })}
          tick={{ fill: CATEGORY_CHART_COLORS.textDim, fontSize: isDesktop ? 11 : 10 }}
        />
        <YAxis
          hide={!isDesktop}
          axisLine={false}
          tickLine={false}
          tick={{ fill: CATEGORY_CHART_COLORS.textDim, fontSize: 11 }}
          tickFormatter={formatCompact}
          width={56}
        />
        <Tooltip content={<MonthTooltip averageMonthly={averageMonthly} />} cursor={false} />
        <Bar dataKey="amount" fill={CATEGORY_CHART_COLORS.bar} radius={[3, 3, 0, 0]} barSize={barSize} isAnimationActive={false}>
          {data.map((entry, index) => (
            <Cell key={`month-${index}`} fillOpacity={entry.partial ? 0.45 : 1} />
          ))}
        </Bar>
        {averageMonthly != null && (
          <ReferenceLine
            y={averageMonthly}
            stroke={CATEGORY_CHART_COLORS.text}
            strokeWidth={1.5}
            strokeDasharray="5 4"
            // The norm's window (M-12..M-1) can include a month older than any bar shown
            // (M-11..M), so a spike in that dropped month can push the average above every
            // bar — without extendDomain the Y axis wouldn't stretch to fit it and the line
            // would be silently discarded instead of drawn at the top edge.
            ifOverflow="extendDomain"
            label={normLabelText ? <NormLabel text={normLabelText} chartHeight={height} /> : undefined}
          />
        )}
      </BarChart>
    </ResponsiveContainer>
  );
}

interface CategoryMonthsChartProps {
  months: CategoryMonthAmount[];
  norm: NormComparison;
  normMonthsCounted: number;
}

export function CategoryMonthsChart({ months, norm, normMonthsCounted }: CategoryMonthsChartProps) {
  const hasNorm = hasNormHistory(norm);
  const averageMonthly = hasNorm ? (norm.averageMonthly as number) : null;
  const currentMonth = months[months.length - 1];
  const data: ChartPoint[] = months.map((month) => ({ ...month, label: monthAxisLabel(month) }));
  // Matched by href, not the displayed label — a copy edit to the nav item's Russian text
  // would otherwise silently fall through to the '/analytics' fallback.
  const analyticsHref = NAV_ITEMS.find((item) => item.href === '/analytics')?.linkTo ?? '/analytics';
  const caption = chartCaption(currentMonth, hasNorm, normMonthsCounted);

  return (
    <div className="glass-card p-4 lg:p-5 flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="font-display text-lg lg:text-[22px] text-app-text">
          <span className="hidden lg:inline">Последние 12 месяцев</span>
          <span className="lg:hidden">12 месяцев</span>
        </h2>
        <div className="hidden lg:flex items-center gap-4 text-app-text-muted text-xs">
          <span className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-[3px] inline-block" style={{ background: CATEGORY_CHART_COLORS.bar }} />
            месяц
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2.5 h-0.5 inline-block" style={{ background: CATEGORY_CHART_COLORS.text }} />
            обычный месяц
          </span>
        </div>
        <span className="lg:hidden text-app-text-dim text-[11px]">пунктир — обычный месяц</span>
      </div>

      {/* Both variants render at all times; Tailwind's lg: breakpoint (1024px) alone decides which
          one is visible, so the chart switches at the exact same width as the header and caption
          above/below it (previously this used useIsMobile's 768px, which disagreed with them
          between 768 and 1024px — a desktop chart under a mobile heading). */}
      <div className="hidden lg:block">
        <MonthsBars data={data} averageMonthly={averageMonthly} height={250} barSize={36} isDesktop />
      </div>
      <div className="lg:hidden">
        <MonthsBars data={data} averageMonthly={averageMonthly} height={90} barSize={14} isDesktop={false} />
      </div>

      <p className="hidden lg:block text-app-text-dim text-[11px]">
        {caption && `${caption}. `}Год против прошлого —{' '}
        <Link to={analyticsHref} className="text-app-accent hover:underline">
          в аналитике
        </Link>
      </p>
    </div>
  );
}
