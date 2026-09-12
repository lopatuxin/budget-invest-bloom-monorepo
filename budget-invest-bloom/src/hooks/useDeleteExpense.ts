import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { invalidateBudgetCaches } from '@/lib/queryKeys';
import type { ApiResponse } from '@/types/budget';

interface DeleteExpenseParams {
  expenseId: string;
}

// No built-in toast here — the operations feed shows its own (success text and
// the 404 "already deleted" case need different wording than a generic error toast).
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
