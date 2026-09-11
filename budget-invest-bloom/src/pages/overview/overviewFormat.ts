// Formatting helpers specific to the overview (capital) page.
// Formatters shared with the investments page (currency signs, compact numbers,
// day-month dates, pluralization) live in '@/lib/dateOptions' — import them from there.
// Month-name formatters (monthShortLabel, monthPrepositional, monthNominative, capitalize)
// live in '@/lib/monthNames', shared with the analytics page.
import { CHART_GRID_COLOR, parseApiDate } from '@/lib/dateOptions';

// Colors that recharts needs as literal values (SVG fill/stroke props take
// plain strings, not Tailwind classes) — kept in sync with the app-* CSS
// tokens in index.css (chart-income / chart-expense / border / text-dim).
// border reuses CHART_GRID_COLOR from '@/lib/dateOptions', shared with the investments page.
export const OVERVIEW_CHART_COLORS = {
  income: '#1E8A4F',
  expense: '#7B5CC7',
  border: CHART_GRID_COLOR,
  textDim: '#9AA0A8',
  text: '#1A1D21',
} as const;

const FULL_DATE_FORMAT = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' });

/** "18 сентября 2026" — page header date */
export function formatFullDate(value: string): string {
  return FULL_DATE_FORMAT.format(parseApiDate(value));
}

/** "50 000" — full space-grouped integer, no currency symbol, for the income chart Y axis */
export function formatThousands(value: number): string {
  return Math.round(value).toLocaleString('ru-RU');
}
