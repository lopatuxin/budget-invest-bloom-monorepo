// Formatting and text helpers specific to the security page's timeline and price card.
// Shared number/date formatters live in '@/lib/dateOptions'; sign/percent helpers shared
// with the investments page live in '@/pages/investments/investmentsFormat'.
import { formatCurrency, formatDayMonth, formatDividendAmount, formatQuantity, formatSignedCurrency, formatUnitPrice, pluralize } from '@/lib/dateOptions';
import { MONTH_SHORT } from '@/lib/dateOptions';
import { signClass } from '@/pages/investments/investmentsFormat';
import type { SecurityEvent, SecurityPagePosition, SecurityPageResult } from '@/types/investment';

// Dot/marker colors from docs/plans/security-page-redesign.md "UI": the price line
// itself reuses PORTFOLIO_CHART_COLOR from pages/investments/investmentsFormat.
export const SECURITY_BUY_COLOR = '#2A78D6';
export const SECURITY_SELL_COLOR = '#B0392B';

// Captions next to a price (today's move, the period's change): a zero move reads as muted
export const SIGN_TEXT_CLASS: Record<'good' | 'bad' | 'neutral', string> = {
  good: 'text-app-good',
  bad: 'text-app-bad',
  neutral: 'text-app-text-muted',
};

// Amounts in the summary cards: a zero result keeps the regular text colour
export const SIGN_AMOUNT_CLASS: Record<'good' | 'bad' | 'neutral', string> = {
  good: 'text-app-good',
  bad: 'text-app-bad',
  neutral: 'text-app-text',
};

export interface PnlRowDisplay {
  label: string;
  amountText: string;
  variant: 'good' | 'bad' | 'neutral';
  badgePercent: number | null;
}

/** The "result" row of both summary cards (p.12, p.22): realised result once the position is
 * closed, «—» while it is open but the exchange gave no price, the price result otherwise */
export function describePnlRow(position: SecurityPagePosition | undefined, result: SecurityPageResult): PnlRowDisplay {
  if (!position) {
    return { label: 'Результат по сделкам', amountText: formatSignedCurrency(result.realizedPnl), variant: signClass(result.realizedPnl), badgePercent: null };
  }
  if (position.pnl == null) {
    return { label: 'Результат по цене', amountText: '—', variant: 'neutral', badgePercent: null };
  }
  return {
    label: 'Результат по цене',
    amountText: formatSignedCurrency(result.pricePnl),
    variant: signClass(result.pricePnl),
    badgePercent: position.pnlPercent ?? null,
  };
}

/** "1 событие" / "2 события" / "7 событий" — mobile timeline header count */
export function pluralEvents(count: number): string {
  return pluralize(count, ['событие', 'события', 'событий']);
}

/** "14 янв 2026" — event row date, from a plain YYYY-MM-DD date string */
export function formatEventDate(value: string): string {
  const [year, month, day] = value.split('-').map(Number);
  return `${day} ${MONTH_SHORT[month - 1]} ${year}`;
}

export type EventAmountVariant = 'plain' | 'good' | 'dim';

export interface EventDisplay {
  title: string;
  subtitle: string;
  amountText: string;
  amountVariant: EventAmountVariant;
}

/** Renders one timeline row's title, subtitle and right-hand amount — the text
 * templates from docs/plans/security-page-redesign.md p.51. */
export function describeEvent(event: SecurityEvent): EventDisplay {
  if (event.kind === 'BUY') {
    const subtitle = event.first
      ? `первая сделка, вложено ${formatCurrency(event.positionAfter.invested)}`
      : `позиция ${formatQuantity(event.positionAfter.quantity)} шт, вложено ${formatCurrency(event.positionAfter.invested)}, средняя ${formatUnitPrice(event.positionAfter.averagePrice)}`;
    return {
      title: `Покупка ${formatQuantity(event.quantity)} шт по ${formatUnitPrice(event.price)}`,
      subtitle,
      amountText: `−${formatCurrency(event.amount)}`,
      amountVariant: 'plain',
    };
  }

  if (event.kind === 'SELL') {
    return {
      title: `Продажа ${formatQuantity(event.quantity)} шт по ${formatUnitPrice(event.price)}`,
      subtitle: `позиция ${formatQuantity(event.positionAfter.quantity)} шт, вложено ${formatCurrency(event.positionAfter.invested)} · результат сделки ${formatSignedCurrency(event.realizedPnl)}`,
      amountText: `+${formatCurrency(event.amount)}`,
      amountVariant: 'plain',
    };
  }

  if (event.kind === 'DIVIDEND_PAID') {
    const manual = event.source === 'MANUAL' ? ' · внесено вручную' : '';
    return {
      title: 'Дивиденды получены',
      subtitle: `${formatDividendAmount(event.amountPerShare, event.currency, 'unit')} на акцию × ${formatQuantity(event.quantity)} шт, после НДФЛ${manual}`,
      amountText: `+${formatDividendAmount(event.netAmount, event.currency)}`,
      amountVariant: 'good',
    };
  }

  // DIVIDEND_UPCOMING: the event doesn't carry the record date itself, only the date it is
  // displayed under (the record date while still ahead, the payment date once it has
  // passed — see p.115) — a payment date equal to that display date is the signal that
  // the backend already made the swap, i.e. the record date is behind us.
  const recordDateAhead = event.paymentDate == null || event.paymentDate !== event.date;
  const paymentLine = event.paymentDate ? `выплата ${formatDayMonth(event.paymentDate)}` : 'дата выплаты не объявлена';
  return {
    title: recordDateAhead ? 'Отсечка по дивидендам' : 'Выплата ожидается',
    subtitle: `${formatDividendAmount(event.amountPerShare, event.currency, 'unit')} на акцию × ${formatQuantity(event.quantity)} шт, ${paymentLine}`,
    amountText: formatDividendAmount(event.netAmount, event.currency),
    amountVariant: 'dim',
  };
}
