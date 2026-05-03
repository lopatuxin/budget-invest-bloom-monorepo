import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, TransactionResponse } from '@/types/investment';

export function useTransactions(ticker?: string) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['investment-transactions', ticker ?? null],
    queryFn: () =>
      apiPost<ApiResponse<TransactionResponse[]>>('/api/investment/transactions/list', ticker ? { ticker } : {}),
  });

  return { data, isLoading, error };
}
