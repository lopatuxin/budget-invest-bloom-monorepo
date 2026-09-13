import { useState } from 'react';
import { ChevronDown, Loader2 } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Command, CommandEmpty, CommandInput, CommandItem, CommandList } from '@/components/ui/command';
import { SecurityLogo } from '@/components/SecurityLogo';
import { useSecuritySearch, type SearchCategory } from '@/hooks/useSecuritySearch';
import { useSecurityList } from '@/hooks/useSecurityList';
import { SECURITY_TYPE_LABEL_SINGULAR } from '@/lib/securityType';
import type { MoexSecuritySearchItem } from '@/types/investment';

interface TransactionSecurityPickerProps {
  value: MoexSecuritySearchItem | null;
  onChange: (item: MoexSecuritySearchItem) => void;
}

// Combobox shared by the transaction dialog opened from the investments page (empty
// value) and from a security page (pre-filled value, list still opens to switch it).
export function TransactionSecurityPicker({ value, onChange }: TransactionSecurityPickerProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<SearchCategory>('STOCKS');

  const isSearchMode = query.trim().length >= 2;
  const { data: searchData, isFetching: searchLoading } = useSecuritySearch(query, category);
  const { data: listData, isFetching: listLoading } = useSecurityList(category, open && !isSearchMode);

  const results: MoexSecuritySearchItem[] = isSearchMode ? searchData?.body ?? [] : listData?.body ?? [];
  const isFetching = isSearchMode ? searchLoading : listLoading;

  const handleSelect = (item: MoexSecuritySearchItem) => {
    onChange(item);
    setQuery('');
    setOpen(false);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          role="combobox"
          aria-expanded={open}
          className="h-10 w-full flex items-center justify-between gap-2.5 rounded-lg border border-app-border-strong bg-app-surface px-3 text-sm text-app-text hover:bg-app-surface-2 transition-colors"
        >
          {value ? (
            <span className="flex items-center gap-2.5 min-w-0">
              <SecurityLogo ticker={value.ticker} size={24} securityType={value.securityType} />
              <span className="font-mono font-semibold shrink-0">{value.ticker}</span>
              <span className="text-app-text-muted truncate">
                {value.name} · {SECURITY_TYPE_LABEL_SINGULAR[value.securityType]}
              </span>
            </span>
          ) : (
            <span className="text-app-text-dim">Выберите бумагу...</span>
          )}
          <ChevronDown aria-hidden="true" className="w-4 h-4 text-app-text-dim shrink-0" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-[380px] p-0 bg-app-surface border-app-border" align="start">
        <div role="tablist" className="flex border-b border-app-border">
          {(['STOCKS', 'BONDS'] as const).map((option) => (
            <button
              key={option}
              type="button"
              role="tab"
              aria-selected={category === option}
              onClick={() => setCategory(option)}
              className={`flex-1 py-2 text-sm transition-colors ${
                category === option ? 'bg-app-surface-2 text-app-text' : 'text-app-text-muted hover:text-app-text'
              }`}
            >
              {option === 'STOCKS' ? 'Акции' : 'Облигации'}
            </button>
          ))}
        </div>
        {/* The shadcn Command pieces paint with the generic shadcn popover/accent/border tokens,
            not the app-* palette, so every surface, text and state colour is overridden with app tokens. */}
        <Command shouldFilter={false} className="bg-app-surface text-app-text [&_[data-cmdk-input-wrapper]]:border-app-border">
          <CommandInput
            placeholder="Поиск по тикеру или названию..."
            value={query}
            onValueChange={setQuery}
            className="text-app-text placeholder:text-app-text-dim"
          />
          <CommandList className="max-h-56" onWheel={(event) => event.stopPropagation()}>
            {isFetching && (
              <div className="flex items-center gap-2 px-3 py-3 text-sm text-app-text-muted">
                <Loader2 aria-hidden="true" className="w-3.5 h-3.5 animate-spin" />
                Загрузка...
              </div>
            )}
            {!isFetching && results.length === 0 && (
              <CommandEmpty className="py-6 text-center text-sm text-app-text-muted">Ничего не найдено</CommandEmpty>
            )}
            {!isFetching &&
              results.map((item) => (
                <CommandItem
                  key={`${item.ticker}-${item.boardId}`}
                  value={item.ticker}
                  onSelect={() => handleSelect(item)}
                  className="flex items-center gap-2.5 px-3 py-2 cursor-pointer rounded-none data-[selected='true']:bg-app-surface-2 data-[selected=true]:text-app-text"
                >
                  <SecurityLogo ticker={item.ticker} size={24} securityType={item.securityType} />
                  <span className="font-mono font-semibold text-app-text">{item.ticker}</span>
                  <span className="text-app-text-muted text-sm truncate flex-1">{item.name}</span>
                  <span className="text-xs text-app-text-dim shrink-0">{SECURITY_TYPE_LABEL_SINGULAR[item.securityType]}</span>
                </CommandItem>
              ))}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
