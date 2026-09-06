import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { invalidateBudgetCaches } from '@/lib/queryKeys';
import type { ApiResponse } from '@/types/budget';

interface DeleteIncomeParams {
  incomeId: string;
}

// No built-in toast — the budget operations feed shows its own (success text and
// the 404 "already deleted" case need different wording than a generic error toast).
export function useDeleteIncome() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params: DeleteIncomeParams) =>
      apiPost<ApiResponse<unknown>>('/api/budget/incomes/delete', params),
    onSuccess: () => {
      invalidateBudgetCaches(queryClient);
    },
  });
}
