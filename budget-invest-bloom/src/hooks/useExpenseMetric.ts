import { useBudgetMetric } from '@/hooks/useBudgetMetric';

export function useExpenseMetric(year: string, enabled = true) {
  return useBudgetMetric('expense', year, enabled);
}
