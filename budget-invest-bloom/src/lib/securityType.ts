import type { SecurityType } from '@/types/investment';

// Plural labels — used for grouping in holdings list
export const SECURITY_TYPE_LABEL: Record<SecurityType, string> = {
  STOCK: 'Акции',
  BOND: 'Облигации',
  OFZ: 'ОФЗ',
  ETF: 'ETF',
};

// Singular labels — used in TransactionSecurityPicker search results
export const SECURITY_TYPE_LABEL_SINGULAR: Record<SecurityType, string> = {
  STOCK: 'Акция',
  BOND: 'Облигация',
  OFZ: 'ОФЗ',
  ETF: 'ETF',
};

// Fallback for a position whose security type the exchange never classified —
// the backend buckets it under a null key (groupPreservingOrder) instead of
// dropping it, so it still needs a label here. Mirrors NO_SECTOR_LABEL.
export const NO_SECURITY_TYPE_LABEL = 'Другое';

export function securityTypeLabel(securityType: SecurityType | null): string {
  return securityType === null ? NO_SECURITY_TYPE_LABEL : SECURITY_TYPE_LABEL[securityType];
}

// A bond/OFZ pays coupons instead of dividends and is priced through its nominal (BondPricing) —
// every "дивиденд"→"купон" label swap keys off this (see docs/plans/forecast-coupons-and-bond-prices.md p.8).
export function isBondSecurityType(securityType: SecurityType): boolean {
  return securityType === 'BOND' || securityType === 'OFZ';
}
