import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk, invalidateBudgetCaches } from '@/lib/queryKeys';
import type { ApiResponse, CreateTransactionRequest, TransactionResponse } from '@/types/investment';

export function useCreateTransaction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateTransactionRequest) =>
      apiPost<ApiResponse<TransactionResponse>>('/api/investment/transactions', data),
    onSuccess: (_data, variables) => {
      // Backend writes BUY/SELL to budget, so invalidate both domains
      invalidateBudgetCaches(queryClient);
      queryClient.invalidateQueries({ queryKey: qk.investment.portfolio() });
      queryClient.invalidateQueries({ queryKey: qk.investment.transactions() });
      queryClient.invalidateQueries({ queryKey: qk.investment.portfolioValueHistory() });
      queryClient.invalidateQueries({ queryKey: qk.investment.positionByTicker(variables.ticker) });
    },
  });
}
