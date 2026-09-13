import { StatTile } from '@/components/StatTile';
import { formatCurrency, formatSignedCurrency, pluralize } from '@/lib/dateOptions';
import type { ProjectionResult } from '@/types/investment';

interface ForecastTilesProps {
  result: ProjectionResult;
  horizonYears: number;
}

export function ForecastTiles({ result, horizonYears }: ForecastTilesProps) {
  const lastPoint = result.series[result.series.length - 1];
  const annualReturnPercent = (result.portfolioWeightedAnnualReturn * 100).toLocaleString('ru-RU', {
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
        subtitle="средняя по истории бумаг портфеля, взвешенная по долям"
      />
      <StatTile
        label="Заработано за срок"
        value={<span className={result.earned >= 0 ? 'text-app-good' : 'text-app-bad'}>{formatSignedCurrency(result.earned)}</span>}
        subtitle="сверх того, что внесли"
      />
    </div>
  );
}
