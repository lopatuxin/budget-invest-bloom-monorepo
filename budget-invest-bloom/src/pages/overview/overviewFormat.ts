// Formatting helpers specific to the overview (capital) page.
import { MONTHS, formatCurrency } from '@/lib/dateOptions';

// Colors that recharts needs as literal values (SVG fill/stroke props take
// plain strings, not Tailwind classes) — kept in sync with the app-* CSS
// tokens in index.css (chart-income / chart-expense / border / text-dim).
export const OVERVIEW_CHART_COLORS = {
  income: '#1E8A4F',
  expense: '#7B5CC7',
  border: '#E4DFD3',
  textDim: '#9AA0A8',
  text: '#1A1D21',
} as const;

const MONTH_SHORT = ['янв', 'фев', 'мар', 'апр', 'май', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'];

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

/** Parses an API date string (YYYY-MM-DD) into a local Date, avoiding UTC shift */
function parseApiDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

const FULL_DATE_FORMAT = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' });
const DAY_MONTH_FORMAT = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long' });

/** "18 сентября 2026" — page header date */
export function formatFullDate(value: string): string {
  return FULL_DATE_FORMAT.format(parseApiDate(value));
}

/** "3 октября" — next dividend payment date */
export function formatDayMonth(value: string): string {
  return DAY_MONTH_FORMAT.format(parseApiDate(value));
}

/** "1,7 млн" / "850 тыс." — Y axis labels for the capital chart */
export function formatCompact(value: number): string {
  const abs = Math.abs(value);
  const sign = value < 0 ? '−' : '';
  if (abs >= 1_000_000) {
    return `${sign}${(abs / 1_000_000).toLocaleString('ru-RU', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} млн`;
  }
  if (abs >= 1_000) {
    return `${sign}${Math.round(abs / 1_000).toLocaleString('ru-RU')} тыс.`;
  }
  return `${sign}${Math.round(abs).toLocaleString('ru-RU')}`;
}

/** "50 000" — full space-grouped integer, no currency symbol, for the income chart Y axis */
export function formatThousands(value: number): string {
  return Math.round(value).toLocaleString('ru-RU');
}

/** "+73 150 ₽" / "−12 000 ₽" — explicit sign for a signed currency amount */
export function formatSignedCurrency(value: number): string {
  return `${value < 0 ? '−' : '+'}${formatCurrency(Math.abs(value))}`;
}

/** "+12,4%" / "−4,0%" — explicit sign, one decimal, comma separator */
export function formatSignedPercent(value: number): string {
  const formatted = Math.abs(value).toLocaleString('ru-RU', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return `${value < 0 ? '−' : '+'}${formatted}%`;
}

/** "1 бумага" / "2 бумаги" / "5 бумаг" */
export function pluralSecurities(count: number): string {
  const mod10 = count % 10;
  const mod100 = count % 100;
  if (mod10 === 1 && mod100 !== 11) return `${count} бумага`;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return `${count} бумаги`;
  return `${count} бумаг`;
}
