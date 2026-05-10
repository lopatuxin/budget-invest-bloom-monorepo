import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { useToast } from '@/hooks/use-toast';
import { qk, invalidateBudgetCaches } from '@/lib/queryKeys';
import type { ApiResponse } from '@/types/investment';

interface DeleteTransactionParams {
  id: string;
  ticker?: string;
}

export function useDeleteTransaction() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: ({ id }: DeleteTransactionParams) =>
      apiPost<ApiResponse<void>>('/api/investment/transactions/delete', { id }),
    onSuccess: (_data, variables) => {
      // Backend removes BUY/SELL from budget, so invalidate both domains
      invalidateBudgetCaches(queryClient);
      queryClient.invalidateQueries({ queryKey: qk.investment.portfolio() });
      queryClient.invalidateQueries({ queryKey: qk.investment.transactions() });
      queryClient.invalidateQueries({ queryKey: qk.investment.portfolioValueHistory() });
      if (variables.ticker) {
        queryClient.invalidateQueries({ queryKey: qk.investment.positionByTicker(variables.ticker) });
      }
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : 'Не удалось удалить сделку';
      toast({ variant: 'destructive', title: 'Ошибка', description: message });
    },
  });
}
