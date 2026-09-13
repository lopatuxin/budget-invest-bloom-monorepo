import { StatTile } from '@/components/StatTile';
import { formatCurrency, formatSignedCurrency, formatSignedPercent, pluralize } from '@/lib/dateOptions';
import { formatPercent } from '@/pages/investments/investmentsFormat';
import type { ProjectionResult } from '@/types/investment';

interface ForecastTilesProps {
  result: ProjectionResult;
  horizonYears: number;
}

export function ForecastTiles({ result, horizonYears }: ForecastTilesProps) {
  const lastPoint = result.series[result.series.length - 1];
  // Sum of the two parts shown in the subtitle, so the tile and its subtitle never disagree by a
  // rounding step (portfolioWeightedAnnualReturn is rounded separately on the server).
  const annualReturnPercent = (result.priceGrowthPercent + result.payoutYieldPercent).toLocaleString('ru-RU', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  });

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <StatTile label="Портфель сейчас" value={formatCurrency(result.startValue)} subtitle="по текущим ценам" />
      <StatTile
        label={`Через ${pluralize(horizonYears, ['год', 'года', 'лет'])}`}
        value={lastPoint ? formatCurrency(lastPoint.value) : '—'}
        subtitle={`из них внесено ${formatCurrency(result.contributedTotal)}`}
      />
      <StatTile
        label="Доходность в год"
        value={`${annualReturnPercent}%`}
        subtitle={`рост цен ${formatSignedPercent(result.priceGrowthPercent)} · выплаты ${formatPercent(result.payoutYieldPercent)} после налога`}
      />
      <StatTile
        label="Заработано за срок"
        value={<span className={result.earned >= 0 ? 'text-app-good' : 'text-app-bad'}>{formatSignedCurrency(result.earned)}</span>}
        subtitle="сверх того, что внесли"
      />
    </div>
  );
}
