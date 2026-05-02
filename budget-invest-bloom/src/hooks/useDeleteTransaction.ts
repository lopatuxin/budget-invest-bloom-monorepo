import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse } from '@/types/investment';

export function useDeleteTransaction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      apiPost<ApiResponse<void>>('/api/investment/transactions/delete', { id }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['investment-portfolio'] });
      queryClient.invalidateQueries({ queryKey: ['investment-transactions'] });
    },
  });
}
