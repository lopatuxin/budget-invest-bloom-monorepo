// Month-name formatters shared across pages (overview, analytics).
import { MONTH_SHORT, MONTHS } from '@/lib/dateOptions';

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
