import { useBudgetMetric } from '@/hooks/useBudgetMetric';

export function useCapitalMetric(year: string, enabled = true) {
  return useBudgetMetric('capital', year, enabled);
}
