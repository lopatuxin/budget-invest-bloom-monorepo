import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, MetricResponse } from '@/types/budget';

export function useExpenseMetric(year: string, enabled = true) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['expense-metric', year],
    queryFn: () =>
      apiPost<ApiResponse<MetricResponse>>('/api/budget/metric/expenses', {
        year: Number(year),
      }),
    enabled,
  });

  return { data, isLoading, error };
}
