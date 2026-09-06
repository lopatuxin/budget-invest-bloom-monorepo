import { useState } from 'react';
import { ru } from 'date-fns/locale';
import { CalendarDays, ChevronDown } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Calendar } from '@/components/ui/calendar';
import { cn } from '@/lib/utils';
import { daysFromToday, formatRelativeDay } from '@/lib/dateOptions';

interface OperationDatePickerProps {
  value: Date;
  onChange: (date: Date) => void;
}

export function OperationDatePicker({ value, onChange }: OperationDatePickerProps) {
  const [open, setOpen] = useState(false);
  const today = daysFromToday(0);
  const yesterday = daysFromToday(-1);
  const isYesterday = value.getTime() === yesterday.getTime();

  return (
    <div className="flex items-center gap-2">
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <button
            type="button"
            className="flex items-center gap-1.5 h-9 px-3 rounded-lg border border-app-border-strong bg-app-surface text-sm text-app-text hover:bg-app-surface-2 transition-colors"
          >
            <CalendarDays aria-hidden="true" className="w-4 h-4 text-app-text-muted" />
            {formatRelativeDay(value)}
            <ChevronDown aria-hidden="true" className="w-3.5 h-3.5 text-app-text-muted" />
          </button>
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0 bg-app-surface border-app-border text-app-text" align="start">
          <Calendar
            mode="single"
            locale={ru}
            selected={value}
            onSelect={(date) => {
              if (date) {
                onChange(date);
                setOpen(false);
              }
            }}
            disabled={{ after: today }}
            initialFocus
          />
        </PopoverContent>
      </Popover>
      <button
        type="button"
        aria-pressed={isYesterday}
        onClick={() => onChange(isYesterday ? today : yesterday)}
        className={cn(
          'h-9 px-3 rounded-lg border text-sm font-medium transition-colors',
          isYesterday
            ? 'bg-app-accent-soft border-app-accent text-app-accent'
            : 'border-app-border-strong text-app-text-muted hover:bg-app-surface-2'
        )}
      >
        Вчера
      </button>
    </div>
  );
}
