import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, PositionResponse } from '@/types/investment';

export function usePositions() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['investment-positions'],
    queryFn: () =>
      apiPost<ApiResponse<PositionResponse[]>>('/api/investment/portfolio/positions', {}),
  });

  return { data, isLoading, error };
}
