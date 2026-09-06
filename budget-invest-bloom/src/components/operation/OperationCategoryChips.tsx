import { cn } from '@/lib/utils';

export interface OperationChipOption {
  value: string;
  label: string;
  emoji?: string;
}

interface OperationCategoryChipsProps {
  options: OperationChipOption[];
  value: string;
  onChange: (value: string) => void;
}

export function OperationCategoryChips({ options, value, onChange }: OperationCategoryChipsProps) {
  return (
    <div className="flex flex-wrap gap-2">
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(option.value)}
            className={cn(
              'flex items-center gap-1.5 h-9 px-3 rounded-full border text-sm font-medium transition-colors',
              selected
                ? 'bg-app-accent-soft border-app-accent text-app-accent'
                : 'bg-app-surface border-app-border text-app-text hover:border-app-border-strong'
            )}
          >
            {option.emoji && <span aria-hidden="true">{option.emoji}</span>}
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
