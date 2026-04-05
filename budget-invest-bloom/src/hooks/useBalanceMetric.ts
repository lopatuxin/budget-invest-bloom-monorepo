import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, MetricResponse } from '@/types/budget';

export function useBalanceMetric(year: string, enabled = true) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['balance-metric', year],
    queryFn: () =>
      apiPost<ApiResponse<MetricResponse>>('/api/budget/metric/balance', {
        year: Number(year),
      }),
    enabled,
  });

  return { data, isLoading, error };
}
