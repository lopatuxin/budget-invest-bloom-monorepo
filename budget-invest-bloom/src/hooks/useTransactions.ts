import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, TransactionResponse } from '@/types/investment';

export function useTransactions(ticker?: string, enabled = true) {
  return useQuery({
    queryKey: qk.investment.transactions(ticker),
    // Convention: all reads go through POST + ApiRequest so gateway validates JWT once.
    queryFn: () =>
      apiPost<ApiResponse<TransactionResponse[]>>('/api/investment/transactions/list', ticker ? { ticker } : {}),
    enabled,
    staleTime: 30_000,
  });
}
