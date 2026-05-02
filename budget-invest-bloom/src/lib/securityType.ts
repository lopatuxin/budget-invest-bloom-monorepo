import type { SecurityType } from '@/types/investment';

// Plural labels — used for grouping in holdings list
export const SECURITY_TYPE_LABEL: Record<SecurityType, string> = {
  STOCK: 'Акции',
  BOND: 'Облигации',
  OFZ: 'ОФЗ',
  ETF: 'ETF',
};

// Singular labels — used in AddAssetDialog search results
export const SECURITY_TYPE_LABEL_SINGULAR: Record<SecurityType, string> = {
  STOCK: 'Акция',
  BOND: 'Облигация',
  OFZ: 'ОФЗ',
  ETF: 'ETF',
};

// Display order for type groups in holdings list
export const SECURITY_TYPE_ORDER: SecurityType[] = ['STOCK', 'BOND', 'OFZ', 'ETF'];
