import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, TransactionResponse } from '@/types/investment';

export function useTransactions() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['investment-transactions'],
    queryFn: () =>
      apiPost<ApiResponse<TransactionResponse[]>>('/api/investment/transactions/list', {}),
  });

  return { data, isLoading, error };
}
