import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, CreateManualDividendRequest, SecurityDividend } from '@/types/investment';

export function useCreateManualDividend() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateManualDividendRequest) =>
      apiPost<ApiResponse<SecurityDividend>>('/api/investment/dividends', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: qk.investment.securityPageAll() });
      queryClient.invalidateQueries({ queryKey: qk.investment.portfolioAll() });
      queryClient.invalidateQueries({ queryKey: qk.budget.overviewPage() });
    },
  });
}
