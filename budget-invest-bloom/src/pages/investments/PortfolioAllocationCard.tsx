import { securityTypeLabel } from '@/lib/securityType';
import { getSectorEmoji } from '@/lib/sectorEmoji';
import {
  formatPercent,
  formatPercentRounded,
  NO_SECTOR_LABEL,
  SECTOR_OTHER_COLOR,
  SECTOR_PALETTE,
} from '@/pages/investments/investmentsFormat';
import type { AllocationItem, PortfolioAllocation, SecurityType } from '@/types/investment';

// Bar segments narrower than this share their label in the legend below
// instead of drawing it inside the segment (approximates the design's
// "wider than 60px" rule without measuring the rendered bar).
const INLINE_LABEL_THRESHOLD_PERCENT = 12;

// Bar background paired with a text color readable on it — mirrors
// BADGE_STYLES elsewhere on the page (bg-app-border-strong and
// bg-app-text-dim are light, so they need dark text, not the white that
// works for the darker bg-app-text/bg-app-neutral segments).
const TYPE_STYLES: Record<SecurityType, { bar: string; label: string }> = {
  STOCK: { bar: 'bg-app-text', label: 'text-white' },
  BOND: { bar: 'bg-app-text-dim', label: 'text-app-text' },
  OFZ: { bar: 'bg-app-neutral', label: 'text-white' },
  ETF: { bar: 'bg-app-border-strong', label: 'text-app-text' },
};

// A position whose type the exchange never classified (backend buckets it
// under a null key rather than dropping it) still needs a bar segment.
const NO_TYPE_STYLE = { bar: 'bg-app-text-muted', label: 'text-white' };

function typeStyle(securityType: SecurityType | null): { bar: string; label: string } {
  return securityType === null ? NO_TYPE_STYLE : TYPE_STYLES[securityType];
}

interface SectorSlice {
  label: string;
  percent: number;
  color: string;
}

function hasPercent(item: AllocationItem): item is AllocationItem & { percent: number } {
  return item.percent !== null;
}

// Sectors beyond the fixed 6-color palette fold into one "Прочее" slice —
// a chart-legend truncation, not a data aggregation (bySector itself is
// already computed and sorted by the backend). Returns null when the shares
// aren't computable at all (totalValue = 0 — exchange down, no snapshot in
// the database), so the caller can show "нет данных" instead of a bar with
// an invalid width.
function bucketSectors(bySector: AllocationItem[]): SectorSlice[] | null {
  if (!bySector.every(hasPercent)) return null;
  const primary = bySector.slice(0, SECTOR_PALETTE.length).map((item, index) => ({
    label: item.sector ?? NO_SECTOR_LABEL,
    percent: item.percent,
    color: SECTOR_PALETTE[index],
  }));
  const overflow = bySector.slice(SECTOR_PALETTE.length);
  if (overflow.length === 0) return primary;
  const otherPercent = overflow.reduce((sum, item) => sum + item.percent, 0);
  return [...primary, { label: 'Прочее', percent: otherPercent, color: SECTOR_OTHER_COLOR }];
}

interface PortfolioAllocationCardProps {
  allocation: PortfolioAllocation;
}

export function PortfolioAllocationCard({ allocation }: PortfolioAllocationCardProps) {
  const typeShares = allocation.byType.every(hasPercent) ? allocation.byType : null;
  const sectorSlices = bucketSectors(allocation.bySector);
  const typeLegendItems = typeShares ? typeShares.filter((item) => item.percent < INLINE_LABEL_THRESHOLD_PERCENT) : [];

  return (
    <div className="glass-card p-4 lg:p-5 flex flex-col gap-2.5">
      <div className="flex items-baseline justify-between">
        <h2 className="font-display text-[20px] lg:text-[22px] text-app-text">Распределение</h2>
        <span className="text-app-text-dim text-xs">доли от текущей стоимости</span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[120px_minmax(0,1fr)] gap-2 lg:gap-3 lg:items-center">
        <span className="hidden lg:inline text-app-text-muted text-xs">Виды бумаг</span>
        {typeShares ? (
          <div className="flex gap-0.5 h-3.5 rounded-md overflow-hidden">
            {typeShares.map((item) => {
              const securityType = item.securityType ?? null;
              const styles = typeStyle(securityType);
              return (
                <div
                  key={securityType ?? '__untyped__'}
                  className={`${styles.bar} flex items-center overflow-hidden whitespace-nowrap`}
                  style={{ width: `${item.percent}%` }}
                >
                  {item.percent >= INLINE_LABEL_THRESHOLD_PERCENT && (
                    <span className={`pl-2 text-[11px] font-mono ${styles.label}`}>
                      {securityTypeLabel(securityType)} {formatPercent(item.percent)}
                    </span>
                  )}
                </div>
              );
            })}
          </div>
        ) : (
          <span className="text-app-text-dim text-xs">нет данных о ценах</span>
        )}
        {typeLegendItems.length > 0 && (
          <span className="lg:col-start-2 text-app-text-muted text-[11px]">
            {typeLegendItems.map((item) => `${securityTypeLabel(item.securityType ?? null)} ${formatPercent(item.percent)}`).join(' · ')}
          </span>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[120px_minmax(0,1fr)] gap-2 lg:gap-3 lg:items-center">
        <span className="hidden lg:inline text-app-text-muted text-xs">Секторы</span>
        {sectorSlices ? (
          <div className="flex gap-0.5 h-3 rounded-md overflow-hidden">
            {sectorSlices.map((slice) => (
              <div key={slice.label} style={{ width: `${slice.percent}%`, backgroundColor: slice.color }} />
            ))}
          </div>
        ) : (
          <span className="text-app-text-dim text-xs">нет данных о ценах</span>
        )}
      </div>

      {sectorSlices && (
        <>
          {/* Desktop legend: swatch + name + one-decimal percent, wraps across lines */}
          <div className="hidden lg:flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-app-text-muted">
            {sectorSlices.map((slice) => (
              <span key={slice.label} className="inline-flex items-center gap-1.5">
                <span aria-hidden="true" className="inline-block w-2.5 h-2.5 rounded-sm" style={{ backgroundColor: slice.color }} />
                {slice.label === 'Прочее' ? slice.label : <>{getSectorEmoji(slice.label)} {slice.label}</>}{' '}
                <span className="font-mono text-app-text">{formatPercent(slice.percent)}</span>
              </span>
            ))}
          </div>

          {/* Mobile legend: one compact line, whole-percent, no swatches */}
          <span className="lg:hidden text-app-text-muted text-[11px]">
            {sectorSlices.map((slice) => `${slice.label} ${formatPercentRounded(slice.percent)}`).join(' · ')}
          </span>
        </>
      )}
    </div>
  );
}
