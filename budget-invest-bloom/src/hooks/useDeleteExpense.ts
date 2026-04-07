import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse } from '@/types/budget';

interface DeleteExpenseParams {
  expenseId: string;
}

export function useDeleteExpense() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params: DeleteExpenseParams) =>
      apiPost<ApiResponse<unknown>>('/api/budget/expenses/delete', params),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categoryAnalytics'] });
    },
  });
}
