import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, IncomeMetricResponse } from '@/types/budget';

export function useIncomeMetric(year: string) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['income-metric', year],
    queryFn: () =>
      apiPost<ApiResponse<IncomeMetricResponse>>('/api/budget/metric/income', {
        year: Number(year),
      }),
  });

  return { data, isLoading, error };
}
