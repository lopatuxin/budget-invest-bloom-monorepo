// Formatting and routing helpers specific to the analytics page.
import { formatCurrency, formatSignedCurrency, formatSignedPercent, pluralize } from '@/lib/dateOptions';
import { capitalize, monthNominative } from '@/lib/monthNames';
import type { AnalyticsSection } from '@/types/budget';

export type AnalyticsTab = 'expenses' | 'income' | 'savings';

const VALID_TABS: readonly string[] = ['expenses', 'income', 'savings'];

/** undefined (bare "/analytics") defaults to "expenses"; an unknown segment returns null so the page can redirect. */
export function parseAnalyticsTab(value: string | undefined): AnalyticsTab | null {
  if (value === undefined) return 'expenses';
  return VALID_TABS.includes(value) ? (value as AnalyticsTab) : null;
}

export function analyticsPath(tab: AnalyticsTab, year: number): string {
  return `/analytics/${tab}?year=${year}`;
}

export const ANALYTICS_TAB_LABELS: Record<AnalyticsTab, string> = {
  expenses: 'Расходы',
  income: 'Доходы',
  savings: 'Сбережения',
};

export const ANALYTICS_TOTAL_LABEL: Record<AnalyticsTab, (year: number) => string> = {
  expenses: (year) => `Расходы за ${year}`,
  income: (year) => `Доходы за ${year}`,
  savings: (year) => `Сбережено за ${year}`,
};

export const ANALYTICS_EXTREMES_LABEL: Record<AnalyticsTab, string> = {
  expenses: 'Самый дорогой месяц',
  income: 'Самый доходный месяц',
  savings: 'Лучший месяц',
};

export const ANALYTICS_EXTREMES_WORST_LABEL: Record<AnalyticsTab, string> = {
  expenses: 'самый дешёвый',
  income: 'самый скромный',
  savings: 'худший',
};

// recharts needs colors as literal strings (SVG fill/stroke props take plain
// strings, not Tailwind classes) — matches --cur/--prev in the analytics mockups.
export const ANALYTICS_CHART_COLORS = { current: '#2A78D6', previous: '#CFC9BD' } as const;

/** "+2,7 п.п." / "−1,1 п.п." — one decimal, explicit sign, percentage-point suffix (category contribution to inflation) */
export function formatContributionPoints(value: number): string {
  const formatted = Math.abs(value).toLocaleString('ru-RU', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return `${value < 0 ? '−' : '+'}${formatted} п.п.`;
}

const MONTHS_WORD: readonly [string, string, string] = ['месяц', 'месяца', 'месяцев'];

/** Tile "totals" subtitle: "{n} месяцев, год не закончен · за весь {P} — {previousTotal}" for the current
 * year, or "за весь год · за {P} — {previousTotal}" otherwise; the "за {P}" part is dropped without previous-year data. */
export function totalTileSubtitle(section: AnalyticsSection, isCurrentYear: boolean, previousYear: number, previousYearHasData: boolean): string {
  const yearPart = isCurrentYear ? `${pluralize(section.monthsCounted, MONTHS_WORD)}, год не закончен` : 'за весь год';
  if (!previousYearHasData) return yearPart;
  return `${yearPart} · за весь ${previousYear} — ${formatCurrency(section.previousTotal)}`;
}

/** Fallback subtitle for a tile whose value is null: the year has no counted months yet
 * ("первый месяц ещё не закончился"), or it does but the previous year has no data for it. */
export function emptyValueSubtitle(monthsCounted: number, previousYear: number): string {
  return monthsCounted === 0 ? 'первый месяц ещё не закончился' : `за ${previousYear} данных нет`;
}

/** Tile "average" subtitle: "в {P} — {previousAverage} в месяц" or "за {P} данных нет" */
export function averageTileSubtitle(section: AnalyticsSection, previousYear: number): string {
  if (section.previousAverage == null) return `за ${previousYear} данных нет`;
  return `в ${previousYear} — ${formatCurrency(section.previousAverage)} в месяц`;
}

/** Chart tooltip "diff" line: "+12 300 ₽ (+14,7%)" — the percent part is dropped when the
 * previous-year month has no amount to divide by. */
export function formatSignedPercentDiff(current: number, previous: number): string {
  const diffText = formatSignedCurrency(current - previous);
  if (previous === 0) return diffText;
  return `${diffText} (${formatSignedPercent(((current - previous) / previous) * 100)})`;
}

/** Tile "extremes" subtitle: "{месяц} · {worstLabel} — {месяц}" from the year's best/worst counted months */
export function extremesTileSubtitle(section: AnalyticsSection, worstLabel: string): string {
  if (!section.maxMonth || !section.minMonth) return 'первый месяц ещё не закончился';
  return `${capitalize(monthNominative(section.maxMonth.month))} · ${worstLabel} — ${monthNominative(section.minMonth.month)}`;
}
