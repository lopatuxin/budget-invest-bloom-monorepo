// Trade markers and the labels drawn over the security price chart: which series point each
// trade lands on, and where its caption and the "средняя" caption go so they never overlap.
import { formatQuantity, formatUnitPrice, MONTH_SHORT, parseApiDate } from '@/lib/dateOptions';
import type { SecurityMarker } from '@/types/investment';

export interface SecurityChartPoint {
  index: number;
  date: string;
  close: number;
}

/** "сен 25" — X axis ticks and the phone chart's first/last date captions */
export function formatChartMonth(dateStr: string | undefined): string {
  if (!dateStr) return '';
  const date = parseApiDate(dateStr);
  return `${MONTH_SHORT[date.getMonth()]} ${String(date.getFullYear()).slice(2)}`;
}

export interface MarkerGroup {
  index: number;
  kind: SecurityMarker['kind'];
  quantities: number[];
}

// Trades snap to the closest price point at or before their date (p.46) — a trade
// older than the loaded series (or falling on a day the exchange was closed) has no
// point of its own, so the loop keeps the last candidate whose date qualifies.
function nearestIndexAtOrBefore(dates: string[], date: string): number | null {
  let result: number | null = null;
  for (let i = 0; i < dates.length; i++) {
    if (dates[i] <= date) result = i;
    else break;
  }
  return result;
}

function joinQuantities(quantities: number[]): string {
  if (quantities.length === 1) return formatQuantity(quantities[0]);
  const parts = quantities.map(formatQuantity);
  return `${parts.slice(0, -1).join(', ')} и ${parts[parts.length - 1]}`;
}

// Multiple same-day, same-kind trades land on one chart point — grouped here so their
// label reads "покупка 4 и 3 шт" instead of two overlapping dots (edge case, p.78).
export function groupMarkers(dates: string[], markers: SecurityMarker[]): MarkerGroup[] {
  const groups = new Map<string, MarkerGroup>();
  for (const marker of markers) {
    const index = nearestIndexAtOrBefore(dates, marker.date);
    if (index === null) continue;
    const key = `${index}-${marker.kind}`;
    const existing = groups.get(key);
    if (existing) existing.quantities.push(marker.quantity);
    else groups.set(key, { index, kind: marker.kind, quantities: [marker.quantity] });
  }
  return [...groups.values()];
}

export interface PlotArea {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

export interface ChartPoint {
  x: number;
  y: number;
}

export interface ChartLabel {
  key: string;
  text: string;
  x: number;
  y: number;
  anchor: 'middle' | 'end';
  kind: SecurityMarker['kind'] | 'AVERAGE';
}

interface Box {
  left: number;
  right: number;
  top: number;
  bottom: number;
}

// SVG text cannot be measured before it is drawn, so 11px captions are sized per character;
// the estimate errs wide, which only adds a little air between boxes.
const CHAR_WIDTH = 6.4;
const TEXT_HEIGHT = 12;
// Marker dot radius 5 plus its 2px white stroke plus a pixel of air
const DOT_CLEARANCE = 8;

function textBox(text: string, x: number, baseline: number, anchor: ChartLabel['anchor']): Box {
  const width = text.length * CHAR_WIDTH;
  const left = anchor === 'end' ? x - width : x - width / 2;
  return { left, right: left + width, top: baseline - TEXT_HEIGHT + 2, bottom: baseline + 2 };
}

function dotBox(point: ChartPoint): Box {
  return { left: point.x - DOT_CLEARANCE, right: point.x + DOT_CLEARANCE, top: point.y - DOT_CLEARANCE, bottom: point.y + DOT_CLEARANCE };
}

function overlaps(a: Box, b: Box): boolean {
  return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
}

function isInside(box: Box, plot: PlotArea): boolean {
  return box.top >= plot.top && box.bottom <= plot.bottom;
}

// Candidates in order of preference: the first one inside the plot and clear of every taken box;
// failing that, the first one at least inside the plot, so a caption never lands on the axis.
function pickBaseline(candidates: number[], boxAt: (baseline: number) => Box, plot: PlotArea, taken: Box[]): number {
  const inside = candidates.filter((candidate) => isInside(boxAt(candidate), plot));
  const free = inside.find((candidate) => !taken.some((box) => overlaps(box, boxAt(candidate))));
  return free ?? inside[0] ?? candidates[0];
}

/**
 * The "средняя" caption sits at the right end of the dashed line, above it unless a dot or the
 * plot edge is in the way, then below it. Each trade caption goes under its dot (p.10), clamped
 * inside the plot horizontally; when that spot is taken it tries above the dot, then one line
 * further out below and above.
 */
export function layoutChartLabels(
  plot: PlotArea,
  markers: { group: MarkerGroup; point: ChartPoint }[],
  average: { text: string; y: number } | null,
  lastPoint: ChartPoint,
): ChartLabel[] {
  const labels: ChartLabel[] = [];
  const taken: Box[] = [...markers.map(({ point }) => dotBox(point)), dotBox(lastPoint)];

  if (average) {
    const x = plot.right - 4;
    const averageBoxAt = (baseline: number) => textBox(average.text, x, baseline, 'end');
    const baseline = pickBaseline([average.y - 5, average.y + TEXT_HEIGHT + 3], averageBoxAt, plot, taken);
    taken.push(averageBoxAt(baseline));
    labels.push({ key: 'average', text: average.text, x, y: baseline, anchor: 'end', kind: 'AVERAGE' });
  }

  for (const { group, point } of markers) {
    const verb = group.kind === 'BUY' ? 'покупка' : group.kind === 'REDEMPTION' ? 'погашение' : 'продажа';
    const text = `${verb} ${joinQuantities(group.quantities)} шт`;
    const halfWidth = (text.length * CHAR_WIDTH) / 2;
    const x = Math.min(Math.max(point.x, plot.left + halfWidth), plot.right - halfWidth);
    const belowBaseline = point.y + DOT_CLEARANCE + TEXT_HEIGHT - 2;
    const aboveBaseline = point.y - DOT_CLEARANCE - 2;
    const markerBoxAt = (candidate: number) => textBox(text, x, candidate, 'middle');
    const baseline = pickBaseline(
      [belowBaseline, aboveBaseline, belowBaseline + TEXT_HEIGHT, aboveBaseline - TEXT_HEIGHT],
      markerBoxAt,
      plot,
      taken,
    );
    taken.push(markerBoxAt(baseline));
    labels.push({ key: `${group.index}-${group.kind}`, text, x, y: baseline, anchor: 'middle', kind: group.kind });
  }
  return labels;
}

export function averagePriceLabel(averagePrice: number): string {
  return `средняя ${formatUnitPrice(averagePrice)}`;
}
