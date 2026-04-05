import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, MetricResponse } from '@/types/budget';

export function useCapitalMetric(year: string, enabled = true) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['capital-metric', year],
    queryFn: () =>
      apiPost<ApiResponse<MetricResponse>>('/api/budget/metric/capital', {
        year: Number(year),
      }),
    enabled,
  });

  return { data, isLoading, error };
}
