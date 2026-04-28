import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import type { ApiResponse, MoexSecuritySearchItem } from '@/types/investment';

export function useSecuritySearch(query: string) {
  const [debouncedQuery, setDebouncedQuery] = useState(query);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(timer);
  }, [query]);

  return useQuery({
    queryKey: ['moex-search', debouncedQuery],
    queryFn: () =>
      apiPost<ApiResponse<MoexSecuritySearchItem[]>>('/api/investment/market/search', {
        q: debouncedQuery,
      }),
    enabled: debouncedQuery.trim().length >= 2,
    staleTime: 60_000,
  });
}
