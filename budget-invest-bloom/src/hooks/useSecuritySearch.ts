import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiPost } from '@/lib/api';
import { qk } from '@/lib/queryKeys';
import type { ApiResponse, MoexSecuritySearchItem } from '@/types/investment';

export type SearchCategory = 'STOCKS' | 'BONDS';

export function useSecuritySearch(query: string, category?: SearchCategory) {
  // Start with empty string so debounce always fires from a clean state on first render
  const [debouncedQuery, setDebouncedQuery] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(timer);
  }, [query]);

  return useQuery({
    queryKey: [...qk.investment.securitySearch(debouncedQuery), category ?? 'ALL'],
    queryFn: () =>
      apiPost<ApiResponse<MoexSecuritySearchItem[]>>('/api/investment/market/search', {
        q: debouncedQuery,
        ...(category ? { category } : {}),
      }),
    enabled: debouncedQuery.trim().length >= 2,
    staleTime: 60_000,
  });
}
