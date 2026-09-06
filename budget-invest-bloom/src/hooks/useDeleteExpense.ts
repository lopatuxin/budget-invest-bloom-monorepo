import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { invalidateBudgetCaches } from '@/lib/queryKeys';
import type { ApiResponse } from '@/types/budget';

interface DeleteExpenseParams {
  expenseId: string;
}

// No built-in toast here — callers show their own (success text and 404 "already
// deleted" handling differ between CategoryExpenses and the budget operations feed).
export function useDeleteExpense() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params: DeleteExpenseParams) =>
      apiPost<ApiResponse<unknown>>('/api/budget/expenses/delete', params),
    onSuccess: () => {
      invalidateBudgetCaches(queryClient);
    },
  });
}
