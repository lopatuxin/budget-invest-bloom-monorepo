import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { useToast } from '@/hooks/use-toast';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse } from '@/types/investment';

interface DeleteManualDividendParams {
  dividendId: string;
  ticker: string;
}

export function useDeleteManualDividend() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: ({ dividendId }: DeleteManualDividendParams) =>
      apiPost<ApiResponse<void>>('/api/investment/dividends/delete', { dividendId }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: qk.investment.securityDividends(variables.ticker) });
      queryClient.invalidateQueries({ queryKey: qk.investment.portfolioAll() });
      queryClient.invalidateQueries({ queryKey: qk.budget.overviewPage() });
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : 'Не удалось удалить дивиденд';
      toast({ variant: 'destructive', title: 'Ошибка', description: message });
    },
  });
}
