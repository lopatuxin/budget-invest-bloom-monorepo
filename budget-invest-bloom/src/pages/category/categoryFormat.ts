// Formatting helpers specific to the category page.
import { CHART_GRID_COLOR, formatCurrency, pluralize } from '@/lib/dateOptions';
import { capitalize, monthNominative, monthPrepositional, monthShortLabel } from '@/lib/monthNames';
import type { BudgetPeriodKind } from '@/pages/budget/budgetPeriod';
import type { CategoryMonthAmount, NormComparison } from '@/types/budget';

const MONTHS_WORD: readonly [string, string, string] = ['месяц', 'месяца', 'месяцев'];
export const EXPENSES_WORD: readonly [string, string, string] = ['расход', 'расхода', 'расходов'];

// recharts needs colors as literal strings (SVG fill/stroke props take plain
// strings, not Tailwind classes) — matches --cur/--text in the category mockups.
export const CATEGORY_CHART_COLORS = {
  bar: '#2A78D6',
  text: '#1A1D21',
  textDim: '#9AA0A8',
  border: CHART_GRID_COLOR,
} as const;

/** A norm is usable once the backend didn't fall back to NO_HISTORY for it. */
export function hasNormHistory(norm: NormComparison): boolean {
  // The backend omits these fields entirely (@JsonInclude(NON_NULL)) rather than sending null,
  // so both undefined and null must count as "no value" here.
  return norm.status !== 'NO_HISTORY' && norm.averageMonthly != null && norm.usualByDay != null;
}

/** "обычно 27 100" — the chart's norm-line label. Unlike formatCurrency elsewhere on this
 * page, the mockup (CategoryDesktop.html) drops the ₽ sign here: "обычно" already reads as
 * money, and the shorter string leaves more of the chart's plot area to the bars. */
export function normLineLabel(averageMonthly: number): string {
  return `обычно ${Math.round(averageMonthly).toLocaleString('ru-RU')}`;
}

export function backToBudgetHref(month: number, year: number): string {
  return `/budget?month=${month}&year=${year}`;
}

/** "Потрачено за сентябрь" — spent tile label */
export function spentTileLabel(month: number): string {
  return `Потрачено за ${monthNominative(month)}`;
}

/** Spent tile subtitle: "обычно к {d}-му — {X} · за полный месяц — {Y}" (current, full), "обычно за месяц — {Y}"
 * (past), "месяц ещё не начался" (future), or a no-history fallback. The compact variant is the phone
 * wording (CategoryMobile.html): the current month drops "за полный месяц — {Y}" here, since it moves
 * to categoryBarCaption's compact line instead — the phone has no room for both quantities on one line. */
export function categorySpentSubtitle(
  periodKind: BudgetPeriodKind,
  norm: NormComparison,
  dayOfMonth: number,
  variant: SubtitleVariant = 'full'
): string {
  if (periodKind === 'future') return 'месяц ещё не начался';
  if (!hasNormHistory(norm)) return periodKind === 'current' ? 'раньше к этому дню трат не было' : 'истории пока нет';
  const usualByDay = formatCurrency(norm.usualByDay as number);
  const averageMonthly = formatCurrency(norm.averageMonthly as number);
  if (periodKind === 'past') return `обычно за месяц — ${averageMonthly}`;
  return variant === 'compact'
    ? `обычно к ${dayOfMonth}-му — ${usualByDay}`
    : `обычно к ${dayOfMonth}-му — ${usualByDay} · за полный месяц — ${averageMonthly}`;
}

/** Norm bar caption: mentions the "usual by today" tick only for the current month (full), or on the
 * phone (compact) states the average monthly amount instead — the counterpart moved out of
 * categorySpentSubtitle's compact line above. */
export function categoryBarCaption(periodKind: BudgetPeriodKind, norm: NormComparison, variant: SubtitleVariant = 'full'): string {
  if (variant === 'compact' && periodKind === 'current') {
    return `за полный месяц обычно ${formatCurrency(norm.averageMonthly as number)}`;
  }
  return periodKind === 'current'
    ? 'полоса — обычный полный месяц · отметка — где вы обычно к сегодняшнему дню'
    : 'полоса — обычный полный месяц';
}

export function usualPerMonthValue(norm: NormComparison): string {
  return hasNormHistory(norm) ? formatCurrency(norm.averageMonthly as number) : '—';
}

export function usualPerMonthSubtitle(norm: NormComparison, normMonthsCounted: number): string {
  if (!hasNormHistory(norm)) return 'истории пока нет';
  // normMonthsCounted is the average's denominator: months in the 12-month window where the
  // user had ANY expenses at all, not months where THIS category had them — say "у вас", not "в категории".
  return `среднее за ${pluralize(normMonthsCounted, MONTHS_WORD)}, когда у вас были расходы`;
}

export function shareValue(sharePercent: number | null | undefined): string {
  return sharePercent == null ? '—' : `${Math.round(sharePercent)}%`;
}

/** The compact variant is the phone wording from the mockup — a half-width tile has no room
 * for the desktop sentence. Same rules, one place, two lengths. */
export type SubtitleVariant = 'full' | 'compact';

export function shareSubtitle(variant: SubtitleVariant = 'full'): string {
  // "—" belongs to the value (shareValue), not the caption — the caption stays textual even
  // when sharePercent is null, so the tile never shows two dashes stacked on top of each other.
  return variant === 'compact' ? 'за 12 месяцев' : 'от всех расходов за 12 месяцев';
}

export function operationsTileLabel(month: number): string {
  return `Операций в ${monthPrepositional(month)}`;
}

export function operationsTileSubtitle(
  operationsCount: number,
  averageCheck: number | null | undefined,
  largestAmount: number | null | undefined,
  variant: SubtitleVariant = 'full'
): string {
  if (operationsCount === 0 || averageCheck == null || largestAmount == null) return 'операций пока нет';
  const average = `средний чек ${formatCurrency(averageCheck)}`;
  return variant === 'compact' ? average : `${average} · крупнейшая ${formatCurrency(largestAmount)}`;
}

/** "окт" for most months, "янв 2026" for January so a year change reads on the axis. */
export function monthAxisLabel(point: CategoryMonthAmount): string {
  const short = monthShortLabel(point.month);
  return point.month === 1 ? `${short} ${point.year}` : short;
}

/** Caption under the 12-month chart: mentions the pale current month only when it's partial,
 * and the norm dashed line only when the category has one. */
export function chartCaption(currentMonth: CategoryMonthAmount | undefined, hasNorm: boolean, normMonthsCounted: number): string {
  const notes: string[] = [];
  if (currentMonth?.partial) {
    notes.push(`${capitalize(monthNominative(currentMonth.month))} не закончен и показан бледнее`);
  }
  if (hasNorm) {
    notes.push(
      `пунктир — среднее за ${pluralize(normMonthsCounted, MONTHS_WORD)}, когда у вас были расходы, та же норма, что на карточке бюджета`
    );
  }
  return notes.join('; ');
}
