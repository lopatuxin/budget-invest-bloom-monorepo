import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, BudgetSummaryResponse } from '@/types/budget';

export function useBudgetSummary(month: string, year: string, enabled = true) {
  return useQuery({
    queryKey: qk.budget.summary(month, year),
    queryFn: () =>
      apiPost<ApiResponse<BudgetSummaryResponse>>('/api/budget/summary', {
        month: Number(month),
        year: Number(year),
      }),
    enabled,
    staleTime: 30_000,
  });
}
