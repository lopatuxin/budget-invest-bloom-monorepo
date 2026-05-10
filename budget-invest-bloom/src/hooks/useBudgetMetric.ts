import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, MetricResponse } from '@/types/budget';

type MetricKind = 'balance' | 'capital' | 'expense' | 'income' | 'inflation';

const METRIC_ENDPOINT: Record<MetricKind, string> = {
  balance: '/api/budget/metric/balance',
  capital: '/api/budget/metric/capital',
  expense: '/api/budget/metric/expenses',
  income: '/api/budget/metric/income',
  inflation: '/api/budget/metric/inflation',
};

export function useBudgetMetric(kind: MetricKind, year: string, enabled = true) {
  return useQuery({
    queryKey: qk.metrics[kind](Number(year)),
    queryFn: () =>
      apiPost<ApiResponse<MetricResponse>>(METRIC_ENDPOINT[kind], {
        year: Number(year),
      }),
    enabled,
    staleTime: 30_000,
  });
}
