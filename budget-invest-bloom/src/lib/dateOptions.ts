// Shared date/period/number formatting utilities used across Budget, the category page,
// the analytics page, and the overview and investments pages

// Short lowercase month names — chart axis labels (overview) and stale-price
// header dates (investments). Single source: both pages' *Format.ts import it
// instead of keeping their own copy.
export const MONTH_SHORT = ['янв', 'фев', 'мар', 'апр', 'май', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'] as const;

// recharts needs colors as literal strings (SVG fill/stroke props take plain
// strings, not Tailwind classes) — kept in sync with --app-border-strong in
// index.css. Single source for the overview and investments chart grids.
export const CHART_GRID_COLOR = '#E4DFD3';

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

/** Formats a number as Russian rubles, no decimals */
export const formatCurrency = (value: number): string =>
  new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency: 'RUB',
    maximumFractionDigits: 0,
  }).format(value);

/**
 * "0,05 ₽" / "5 062,50 ₽" — a single unit's price, always two decimals.
 * Totals use formatCurrency's whole rubles, but a penny stock's price rounds
 * to "0 ₽" there, so anything quoted per share or per lot needs this instead.
 */
export const formatUnitPrice = (value: number): string =>
  new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency: 'RUB',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);

/**
 * A dividend amount in its own currency: RUB keeps formatCurrency/formatUnitPrice's
 * ₽ symbol, any other currency shows the number with its code instead — the rule from
 * docs/plans/dividends-tinvest.md Edge cases (dividend paid in a foreign currency).
 */
export const formatDividendAmount = (value: number, currency: string, variant: 'unit' | 'total' = 'total'): string => {
  // Rows synced by the retired MOEX source kept the exchange's own code ("SUR") and are
  // only normalised on the next T-Invest sync, so the check tolerates case and a missing value.
  const code = (currency ?? 'RUB').toUpperCase();
  if (code === 'RUB') {
    return variant === 'unit' ? formatUnitPrice(value) : formatCurrency(value);
  }
  // Foreign amounts stay at two decimals even as a total: a dividend in dollars is
  // ~90 times smaller than the same figure in rubles and rounds to "0 USD" otherwise.
  const formatted = value.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return `${formatted} ${code}`;
};

/** "90 000" / "12,5" — a holding size, grouped by thousands like every other figure */
export const formatQuantity = (value: number): string =>
  value.toLocaleString('ru-RU', { maximumFractionDigits: 4 });

/** "10,5" → 10.5 — a text input with inputMode="decimal" gets a comma from Russian keyboards
 * and phone keypads, which Number() alone turns into NaN; NaN is still returned for non-numbers */
export const parseDecimalInput = (raw: string | number): number =>
  typeof raw === 'number' ? raw : Number(raw.replace(/\s/g, '').replace(',', '.'));

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

/** Parses an API date string (YYYY-MM-DD) into a local Date, avoiding UTC shift */
export const parseApiDate = (value: string): Date => {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
};

/** "4 сентября" — from a plain YYYY-MM-DD date string (dividend record date, transaction date) */
export const formatDayMonth = (value: string): string => DAY_MONTH_FORMAT.format(parseApiDate(value));

interface DividendDates {
  recordDate: string;
  paymentDate?: string | null;
}

/** "отсечка 18 июля" / "выплата 1 августа" / "выплачено 1 августа" — the row
 * label rule from docs/plans/dividends-tinvest.md p.21: an upcoming dividend
 * is labelled by the record date while it is still ahead, otherwise by the
 * payment date; a received one is labelled by the payment date, falling back
 * to the record date when no payment date is known. */
export const formatDividendDateLabel = (dividend: DividendDates, variant: 'upcoming' | 'received'): string => {
  if (variant === 'received') {
    return dividend.paymentDate ? `выплачено ${formatDayMonth(dividend.paymentDate)}` : `отсечка ${formatDayMonth(dividend.recordDate)}`;
  }
  const today = toApiDateString(new Date());
  return dividend.recordDate >= today
    ? `отсечка ${formatDayMonth(dividend.recordDate)}`
    : `выплата ${formatDayMonth(dividend.paymentDate ?? dividend.recordDate)}`;
};

/** "1,7 млн" / "700 тыс." — chart Y axis labels */
export const formatCompact = (value: number): string => {
  const abs = Math.abs(value);
  const sign = value < 0 ? '−' : '';
  if (abs >= 1_000_000) {
    return `${sign}${(abs / 1_000_000).toLocaleString('ru-RU', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} млн`;
  }
  if (abs >= 1_000) {
    return `${sign}${Math.round(abs / 1_000).toLocaleString('ru-RU')} тыс.`;
  }
  return `${sign}${Math.round(abs).toLocaleString('ru-RU')}`;
};

/** "+73 150 ₽" / "−12 000 ₽" — explicit sign for a signed currency amount */
export const formatSignedCurrency = (value: number): string =>
  `${value < 0 ? '−' : '+'}${formatCurrency(Math.abs(value))}`;

/** "+12,4%" / "−4,0%" — explicit sign, one decimal, comma separator */
export const formatSignedPercent = (value: number): string => {
  const formatted = Math.abs(value).toLocaleString('ru-RU', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return `${value < 0 ? '−' : '+'}${formatted}%`;
};

/** Russian plural forms by count: [one, few, many], e.g. ['бумага', 'бумаги', 'бумаг'] */
export const pluralize = (count: number, forms: readonly [string, string, string]): string => {
  const mod10 = count % 10;
  const mod100 = count % 100;
  if (mod10 === 1 && mod100 !== 11) return `${count} ${forms[0]}`;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return `${count} ${forms[1]}`;
  return `${count} ${forms[2]}`;
};

/** "1 бумага" / "2 бумаги" / "5 бумаг" */
export const pluralSecurities = (count: number): string => pluralize(count, ['бумага', 'бумаги', 'бумаг']);
