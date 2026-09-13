// Formatting and text helpers specific to the forecast page's breakdown card
// (docs/plans/forecast-coupons-and-bond-prices.md p.15).
import type { ProjectionBreakdownItem } from '@/types/investment';

/** "дивиденды" / "купоны" / "выплат нет" — the payout-kind subtitle under the Выплаты column
 * before the "нет с {год}" override (describePayoutSubtitle) takes over. */
export function payoutKindLabel(payoutKind: ProjectionBreakdownItem['payoutKind']): string {
  if (payoutKind === 'DIVIDEND') return 'дивиденды';
  if (payoutKind === 'COUPON') return 'купоны';
  return 'выплат нет';
}

export interface PayoutSubtitle {
  text: string;
  isWarn: boolean;
}

/** "нет с {год}" wins over the plain payout-kind label once a security has gone quiet for at
 * least a full year (p.15) — e.g. Газпром keeps payoutKind DIVIDEND from its synced history but
 * hasn't paid since 2022, so lastPayoutYear alone decides, not payoutKind. */
export function describePayoutSubtitle(item: ProjectionBreakdownItem, today: Date = new Date()): PayoutSubtitle {
  const lastFullYear = today.getFullYear() - 1;
  if (item.lastPayoutYear != null && item.lastPayoutYear < lastFullYear) {
    return { text: `нет с ${item.lastPayoutYear}`, isWarn: true };
  }
  return { text: payoutKindLabel(item.payoutKind), isWarn: false };
}
