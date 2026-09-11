import { StatTile } from '@/components/StatTile';
import { BudgetNormBadge } from '@/pages/budget/BudgetNormBadge';
import { formatCurrency, formatSignedPercent } from '@/lib/dateOptions';
import {
  ANALYTICS_EXTREMES_LABEL,
  ANALYTICS_EXTREMES_WORST_LABEL,
  ANALYTICS_TOTAL_LABEL,
  averageTileSubtitle,
  emptyValueSubtitle,
  extremesTileSubtitle,
  totalTileSubtitle,
  type AnalyticsTab,
} from '@/pages/analytics/analyticsFormat';
import type { AnalyticsPageResponse, AnalyticsSection } from '@/types/budget';

function sectionFor(tab: AnalyticsTab, data: AnalyticsPageResponse): AnalyticsSection {
  return tab === 'expenses' ? data.expenses : tab === 'income' ? data.income : data.savings;
}

interface FourthTileProps {
  tab: AnalyticsTab;
  data: AnalyticsPageResponse;
  expenses: AnalyticsSection;
}

// Tile 4 differs the most between tabs: personal inflation, savings total, or the savings rate.
function FourthTile({ tab, data, expenses }: FourthTileProps) {
  if (tab === 'income') {
    return (
      <StatTile
        label={`Сбережено за ${data.year}`}
        value={formatCurrency(data.savings.total)}
        subtitle={data.savingsRatePercent == null ? 'нет данных о норме сбережений' : `норма сбережений ${data.savingsRatePercent}%`}
      />
    );
  }

  if (tab === 'savings') {
    return (
      <StatTile
        label="Норма сбережений"
        value={data.savingsRatePercent == null ? '—' : `${data.savingsRatePercent}%`}
        subtitle={
          data.previousSavingsRatePercent == null
            ? emptyValueSubtitle(data.savings.monthsCounted, data.previousYear)
            : `в ${data.previousYear} — ${data.previousSavingsRatePercent}%`
        }
      />
    );
  }

  return (
    <StatTile
      label="Личная инфляция"
      value={data.personalInflationPercent == null ? '—' : formatSignedPercent(data.personalInflationPercent)}
      subtitle={
        expenses.monthsCounted === 0
          ? 'первый месяц ещё не закончился'
          : data.personalInflationPercent == null
            ? `нужны данные за ${data.previousYear}`
            : `средний месяц ${data.year} против ${data.previousYear} · разбор по категориям ниже`
      }
    />
  );
}

interface AnalyticsTilesProps {
  tab: AnalyticsTab;
  data: AnalyticsPageResponse;
}

export function AnalyticsTiles({ tab, data }: AnalyticsTilesProps) {
  const section = sectionFor(tab, data);
  const isCurrentYear = data.year === data.currentMonth.year;
  const badgeVariant = tab === 'expenses' ? 'expense' : 'income';

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-2.5 lg:gap-4">
      <StatTile
        label={ANALYTICS_TOTAL_LABEL[tab](data.year)}
        value={<span className={section.total < 0 ? 'text-app-bad' : undefined}>{formatCurrency(section.total)}</span>}
        subtitle={totalTileSubtitle(section, isCurrentYear, data.previousYear, data.previousYearHasData)}
      />

      <StatTile
        label="В среднем за месяц"
        value={section.average == null ? '—' : formatCurrency(section.average)}
        valueExtra={
          section.average == null ? null : (
            <BudgetNormBadge deviationPercent={section.change.percent} status={section.change.status} variant={badgeVariant} />
          )
        }
        subtitle={
          section.average == null ? emptyValueSubtitle(section.monthsCounted, data.previousYear) : averageTileSubtitle(section, data.previousYear)
        }
      />

      <StatTile
        label={ANALYTICS_EXTREMES_LABEL[tab]}
        value={section.maxMonth == null ? '—' : formatCurrency(section.maxMonth.amount)}
        subtitle={extremesTileSubtitle(section, ANALYTICS_EXTREMES_WORST_LABEL[tab])}
      />

      <FourthTile tab={tab} data={data} expenses={data.expenses} />
    </div>
  );
}
