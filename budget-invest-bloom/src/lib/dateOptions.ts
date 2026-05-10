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
