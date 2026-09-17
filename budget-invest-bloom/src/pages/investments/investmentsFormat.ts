// Formatting helpers specific to the investments (portfolio) page.
// Formatters shared with the overview page (currency signs, compact numbers,
// day-month dates, pluralization) live in '@/lib/dateOptions' — import them from there.
import { MONTH_SHORT, pluralize } from '@/lib/dateOptions';

// Colors that recharts needs as literal values (SVG fill/stroke props take
// plain strings, not Tailwind classes) — kept in sync with the app-* CSS
// tokens in index.css and the sector palette in Фазы Финансов/Фаза-03-страница-инвестиций.md.
// CHART_GRID_COLOR itself lives in '@/lib/dateOptions', shared with the overview page.
export { CHART_GRID_COLOR } from '@/lib/dateOptions';
export const PORTFOLIO_CHART_COLOR = '#1E8A4F';
export const CHART_TEXT_DIM_COLOR = '#9AA0A8';
export const CHART_TEXT_COLOR = '#1A1D21';

// Fixed order, assigned by descending sector share; a 7th+ sector falls back
// to the "Прочее" bucket colored with border-strong (see PortfolioAllocationCard).
export const SECTOR_PALETTE = ['#2A78D6', '#EB6834', '#1BAF7A', '#EDA100', '#E87BA4', '#008300'] as const;
export const SECTOR_OTHER_COLOR = '#D2CCBE';

// Fallback label for a stock with no sector (PENDING securities) and for the
// non-stock groups (bond/OFZ/ETF), which carry a single sector-less bucket —
// the one place this string is defined on the client (backend uses the same
// literal for the equivalent case).
export const NO_SECTOR_LABEL = 'Без сектора';

const DAY_MONTH_FORMAT = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long' });

/** "1 сектор" / "2 сектора" / "5 секторов" */
export function pluralSectors(count: number): string {
  return pluralize(count, ['сектор', 'сектора', 'секторов']);
}

/** "1 сделка" / "2 сделки" / "5 сделок" */
export function pluralTransactions(count: number): string {
  return pluralize(count, ['сделка', 'сделки', 'сделок']);
}

/** "4 сентября" — same as formatDayMonth (lib/dateOptions), from a full instant (transaction executedAt) */
export function formatInstantDayMonth(value: string): string {
  return DAY_MONTH_FORMAT.format(new Date(value));
}

/** "18:45" — from a full instant (overview.pricesAsOf) */
export function formatTime(value: string): string {
  return new Date(value).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
}

/** "4 сен 18:45" — stale-prices header label, from a full instant */
export function formatDateTime(value: string): string {
  const date = new Date(value);
  return `${date.getDate()} ${MONTH_SHORT[date.getMonth()]} ${formatTime(value)}`;
}

/** "35,6%" — one decimal, no sign (allocation shares) */
export function formatPercent(value: number): string {
  return `${value.toLocaleString('ru-RU', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`;
}

/** Same as formatPercent, but "нет данных" when the share can't be computed
 * (totalValue = 0 — exchange down with no snapshot in the database at all,
 * see PortfolioGroups/PortfolioAllocationCard) instead of a misleading 0,0%. */
export function formatPercentOrUnknown(value: number | null): string {
  return value === null ? 'нет данных' : formatPercent(value);
}

/** "36%" — rounded to a whole percent (compact mobile allocation legend) */
export function formatPercentRounded(value: number): string {
  return `${Math.round(value)}%`;
}

/** "13%" for a whole rate, "13,5%" for a fractional one — DIVIDEND_TAX_RATE
 * is a 0-1 env value and can be fractional, so rounding it to "13%" would
 * misstate the actual rate (dividend tax caption). */
export function formatTaxRatePercent(value: number): string {
  return `${value.toLocaleString('ru-RU', { minimumFractionDigits: 0, maximumFractionDigits: 1 })}%`;
}

/** 'good' / 'bad' / 'neutral' — the three-way sign coloring used across pnl badges and rows */
export function signClass(value: number): 'good' | 'bad' | 'neutral' {
  if (value > 0) return 'good';
  if (value < 0) return 'bad';
  return 'neutral';
}
