import { useBudgetMetric } from '@/hooks/useBudgetMetric';

export function useIncomeMetric(year: string, enabled = true) {
  return useBudgetMetric('income', year, enabled);
}
