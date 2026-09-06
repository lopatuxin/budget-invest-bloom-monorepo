// Shared date/period utilities used across Budget, CategoryExpenses, MetricDetails

export const MONTHS = [
  { value: '1',  label: 'Январь' },
  { value: '2',  label: 'Февраль' },
  { value: '3',  label: 'Март' },
  { value: '4',  label: 'Апрель' },
  { value: '5',  label: 'Май' },
  { value: '6',  label: 'Июнь' },
  { value: '7',  label: 'Июль' },
  { value: '8',  label: 'Август' },
  { value: '9',  label: 'Сентябрь' },
  { value: '10', label: 'Октябрь' },
  { value: '11', label: 'Ноябрь' },
  { value: '12', label: 'Декабрь' },
] as const;

const YEAR_MIN = 2020;

/** Returns string year options from YEAR_MIN to currentYear + 1 */
export function getYearOptions(): string[] {
  const currentYear = new Date().getFullYear();
  return Array.from({ length: currentYear - YEAR_MIN + 2 }, (_, i) => String(YEAR_MIN + i));
}

/** Formats a number as Russian rubles, no decimals */
export const formatCurrency = (value: number): string =>
  new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency: 'RUB',
    maximumFractionDigits: 0,
  }).format(value);

/** Formats a local Date as YYYY-MM-DD without any UTC shift (unlike toISOString) */
export const toApiDateString = (date: Date): string => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

/** Returns a new Date at local midnight, n days offset from today */
export const daysFromToday = (offset: number): Date => {
  const date = new Date();
  date.setHours(0, 0, 0, 0);
  date.setDate(date.getDate() + offset);
  return date;
};

const DAY_MONTH_FORMAT = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long' });

/** "Сегодня"/"Вчера" for the two nearest days, otherwise "16 сентября" */
export const formatRelativeDay = (date: Date): string => {
  const today = daysFromToday(0);
  const yesterday = daysFromToday(-1);
  if (date.getTime() === today.getTime()) return 'Сегодня';
  if (date.getTime() === yesterday.getTime()) return 'Вчера';
  return DAY_MONTH_FORMAT.format(date);
};
