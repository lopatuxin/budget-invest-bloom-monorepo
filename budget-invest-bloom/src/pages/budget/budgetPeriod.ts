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
