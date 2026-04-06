import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, MetricResponse } from '@/types/budget';

export function useInflationMetric(year: string, enabled = true) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['inflation-metric', year],
    queryFn: () =>
      apiPost<ApiResponse<MetricResponse>>('/api/budget/metric/inflation', {
        year: Number(year),
      }),
    enabled,
  });

  return { data, isLoading, error };
}
