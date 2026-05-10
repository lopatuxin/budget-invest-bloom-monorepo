import { useBudgetMetric } from '@/hooks/useBudgetMetric';

export function useBalanceMetric(year: string, enabled = true) {
  return useBudgetMetric('balance', year, enabled);
}
