import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { useToast } from '@/hooks/use-toast';
import { invalidateBudgetCaches } from '@/lib/queryKeys';
import type { ApiResponse } from '@/types/budget';

interface DeleteExpenseParams {
  expenseId: string;
}

export function useDeleteExpense() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: (params: DeleteExpenseParams) =>
      apiPost<ApiResponse<unknown>>('/api/budget/expenses/delete', params),
    onSuccess: () => {
      invalidateBudgetCaches(queryClient);
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : 'Не удалось удалить расход';
      toast({ variant: 'destructive', title: 'Ошибка', description: message });
    },
  });
}
