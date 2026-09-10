// Formatting helpers specific to the overview (capital) page.
// Formatters shared with the investments page (currency signs, compact numbers,
// day-month dates, pluralization) live in '@/lib/dateOptions' — import them from there.
import { CHART_GRID_COLOR, MONTH_SHORT, MONTHS, parseApiDate } from '@/lib/dateOptions';

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

const MONTH_PREPOSITIONAL = [
  'январе', 'феврале', 'марте', 'апреле', 'мае', 'июне',
  'июле', 'августе', 'сентябре', 'октябре', 'ноябре', 'декабре',
];

/** "окт" — short lowercase month name for chart axis labels */
export function monthShortLabel(month: number): string {
  return MONTH_SHORT[month - 1] ?? String(month);
}

/** "сентябре" — for "в {месяце}" phrases */
export function monthPrepositional(month: number): string {
  return MONTH_PREPOSITIONAL[month - 1] ?? String(month);
}

/** "сентябрь" — nominative, lowercase, for standalone sentences ("{месяц} ещё не закончился") */
export function monthNominative(month: number): string {
  return (MONTHS.find((m) => Number(m.value) === month)?.label ?? String(month)).toLowerCase();
}

export function capitalize(value: string): string {
  return value.length === 0 ? value : value.charAt(0).toUpperCase() + value.slice(1);
}

const FULL_DATE_FORMAT = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' });

/** "18 сентября 2026" — page header date */
export function formatFullDate(value: string): string {
  return FULL_DATE_FORMAT.format(parseApiDate(value));
}

/** "50 000" — full space-grouped integer, no currency symbol, for the income chart Y axis */
export function formatThousands(value: number): string {
  return Math.round(value).toLocaleString('ru-RU');
}
