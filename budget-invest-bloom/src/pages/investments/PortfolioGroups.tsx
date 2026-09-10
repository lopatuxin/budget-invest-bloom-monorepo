import { Plus } from 'lucide-react';
import { PositionCard } from '@/pages/investments/PositionCard';
import { securityTypeLabel } from '@/lib/securityType';
import { getSectorEmoji } from '@/lib/sectorEmoji';
import { formatCurrency, pluralSecurities } from '@/lib/dateOptions';
import { formatPercentOrUnknown, NO_SECTOR_LABEL } from '@/pages/investments/investmentsFormat';
import type { PortfolioSort, PositionGroup } from '@/types/investment';

const SORT_OPTIONS: { value: PortfolioSort; label: string }[] = [
  { value: 'WEIGHT', label: 'по доле' },
  { value: 'PNL', label: 'по результату' },
  { value: 'DAY', label: 'за день' },
];

function NewTransactionCard({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-[118px] flex-col items-center justify-center gap-2 rounded-[10px] border border-dashed border-app-border-strong text-app-text-muted transition-colors hover:border-app-accent hover:text-app-accent"
    >
      <Plus aria-hidden="true" className="w-4 h-4" />
      <span className="text-sm font-medium">Новая сделка</span>
    </button>
  );
}

interface PortfolioGroupsProps {
  groups: PositionGroup[];
  sort: PortfolioSort;
  onSortChange: (sort: PortfolioSort) => void;
  onAddTransaction: () => void;
}

// Groups, sectors and positions arrive from the backend already ordered and
// sorted (fixed type order, sectors by share, positions by the chosen sort) —
// this component only labels and lays them out, it does not re-sort.
export function PortfolioGroups({ groups, sort, onSortChange, onAddTransaction }: PortfolioGroupsProps) {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between px-0.5">
        <h2 className="font-display text-[20px] lg:text-[22px] text-app-text">Бумаги</h2>
        <div className="hidden lg:flex items-center gap-1.5">
          <span className="text-app-text-muted text-xs">внутри группы</span>
          {SORT_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => onSortChange(option.value)}
              className={`h-[26px] px-2.5 rounded-full font-mono text-xs ${
                sort === option.value ? 'bg-app-surface-2 border border-app-border text-app-text' : 'text-app-text-muted hover:text-app-text'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex flex-col gap-[18px]">
        {groups.map((group, groupIndex) => {
          const isLastGroup = groupIndex === groups.length - 1;
          const isUnsectored = group.sectors.length === 1 && group.sectors[0].sector === null;

          return (
            <div key={group.securityType ?? '__untyped__'} className="flex flex-col gap-2.5">
              {/* Figures sit next to the heading rather than pushed to the far edge: on a
                  four-column grid justify-between strands them across an empty gap. */}
              <div className="flex items-baseline gap-2.5 border-b border-app-border-strong pb-2">
                <span className="font-display text-lg lg:text-xl text-app-text">{securityTypeLabel(group.securityType)}</span>
                <span className="font-mono text-app-text-muted text-xs lg:text-[13px]">
                  {formatCurrency(group.value)} · {formatPercentOrUnknown(group.percent)}
                  <span className="hidden lg:inline"> · {pluralSecurities(group.assetsCount)}</span>
                </span>
              </div>

              {group.sectors.map((sector, sectorIndex) => {
                const isLastSector = isLastGroup && sectorIndex === group.sectors.length - 1;
                const sectorLabel = sector.sector ?? NO_SECTOR_LABEL;

                return (
                  <div key={sector.sector ?? '__unsectored__'} className="flex flex-col gap-2.5">
                    {!isUnsectored && (
                      <div className="flex items-baseline gap-2 px-0.5 text-[13px] font-medium text-app-text">
                        <span>
                          {getSectorEmoji(sectorLabel)} {sectorLabel}
                        </span>
                        <span className="font-mono text-app-text-dim text-xs">
                          {formatPercentOrUnknown(sector.percent)} · {pluralSecurities(sector.assetsCount)}
                        </span>
                      </div>
                    )}
                    <div className="grid grid-cols-2 lg:grid-cols-4 gap-2.5 lg:gap-3.5">
                      {sector.positions.map((position) => (
                        <PositionCard key={position.id} position={position} />
                      ))}
                      {isLastSector && <NewTransactionCard onClick={onAddTransaction} />}
                    </div>
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}
