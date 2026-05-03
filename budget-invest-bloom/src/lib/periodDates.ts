export type Period = '1M' | '3M' | '1Y' | 'MAX';

export function getPeriodDates(period: Period): { from: string; to: string } {
  const today = new Date();
  const to = today.toISOString().slice(0, 10);

  const from = new Date(today);
  if (period === '1M') {
    from.setMonth(from.getMonth() - 1);
  } else if (period === '3M') {
    from.setMonth(from.getMonth() - 3);
  } else if (period === '1Y') {
    from.setFullYear(from.getFullYear() - 1);
  } else {
    return { from: '2010-01-01', to };
  }

  return { from: from.toISOString().slice(0, 10), to };
}
