import { useBudgetMetric } from '@/hooks/useBudgetMetric';

export function useInflationMetric(year: string, enabled = true) {
  return useBudgetMetric('inflation', year, enabled);
}
