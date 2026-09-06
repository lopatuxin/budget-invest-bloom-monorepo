import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, OperationsResponse } from '@/types/budget';

export function useBudgetOperations(month: string, year: string, enabled = true) {
  return useQuery({
    queryKey: qk.budget.operations(month, year),
    queryFn: () =>
      apiPost<ApiResponse<OperationsResponse>>('/api/budget/operations', {
        month: Number(month),
        year: Number(year),
      }),
    enabled,
    staleTime: 30_000,
  });
}
