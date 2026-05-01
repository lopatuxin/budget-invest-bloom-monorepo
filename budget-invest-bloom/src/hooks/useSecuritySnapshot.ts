import { useMutation } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse } from '@/types/investment';

export interface MoexSnapshot {
  ticker: string;
  lastPrice: number | null;
  previousClose: number | null;
  fetchedAt: string | null;
  stale: boolean;
}

export function useSecuritySnapshot() {
  return useMutation({
    mutationFn: (ticker: string) =>
      apiPost<ApiResponse<MoexSnapshot>>('/api/investment/market/snapshot', { ticker }),
  });
}
