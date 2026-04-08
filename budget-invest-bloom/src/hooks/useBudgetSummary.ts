import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, BudgetSummaryResponse } from '@/types/budget';

export function useBudgetSummary(month: string, year: string, enabled = true) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['budget-summary', month, year],
    queryFn: () =>
      apiPost<ApiResponse<BudgetSummaryResponse>>('/api/budget/summary', {
        month: Number(month),
        year: Number(year),
      }),
    enabled,
  });

  return { data, isLoading, error };
}
