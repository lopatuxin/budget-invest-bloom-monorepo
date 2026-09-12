export type BudgetPeriodKind = 'current' | 'past' | 'future';

/** Compares a requested (month, year) against today's calendar month */
export function getBudgetPeriodKind(month: number, year: number): BudgetPeriodKind {
  const now = new Date();
  const currentMonth = now.getMonth() + 1;
  const currentYear = now.getFullYear();
  if (year > currentYear || (year === currentYear && month > currentMonth)) return 'future';
  if (year === currentYear && month === currentMonth) return 'current';
  return 'past';
}

/** "N-й день из M" (current month, once the day counts are known) / "полный месяц" (past) /
 * "месяц ещё не начался" (future). Shared by the budget page header and the category page header. */
export function periodSubtitle(
  periodKind: BudgetPeriodKind,
  dayOfMonth: number | null | undefined,
  daysInMonth: number | null | undefined
): string {
  if (periodKind === 'future') return 'месяц ещё не начался';
  if (periodKind === 'past') return 'полный месяц';
  if (dayOfMonth == null || daysInMonth == null) return '';
  return `${dayOfMonth}-й день из ${daysInMonth}`;
}
