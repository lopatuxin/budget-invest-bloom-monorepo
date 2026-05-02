import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, CreateTransactionRequest, TransactionResponse } from '@/types/investment';

export function useCreateTransaction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateTransactionRequest) =>
      apiPost<ApiResponse<TransactionResponse>>('/api/investment/transactions', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['investment-portfolio'] });
      queryClient.invalidateQueries({ queryKey: ['investment-transactions'] });
    },
  });
}
