import { useState, useRef, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Plus, Loader2 } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { useCreateTransaction } from '@/hooks/useCreateTransaction';
import { useSecuritySearch } from '@/hooks/useSecuritySearch';
import type { MoexSecuritySearchItem } from '@/types/investment';

const schema = z.object({
  ticker: z.string().trim().min(1, 'Обязательное поле').max(16).transform(v => v.toUpperCase()),
  type: z.enum(['BUY', 'SELL']),
  securityType: z.enum(['STOCK', 'BOND']),
  quantity: z.coerce.number().positive('Должно быть > 0'),
  price: z.coerce.number().positive('Должно быть > 0'),
  executedAt: z.string().min(1, 'Обязательное поле'),
});

type FormValues = z.infer<typeof schema>;

interface AddAssetDialogProps {
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

const AddAssetDialog = ({ open, onOpenChange }: AddAssetDialogProps) => {
  const { toast } = useToast();
  const { mutateAsync, isPending } = useCreateTransaction();

  // Ticker autocomplete state
  const [tickerInput, setTickerInput] = useState('');
  const [selectedSecurityName, setSelectedSecurityName] = useState<string | null>(null);
  const [showDropdown, setShowDropdown] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const tickerInputRef = useRef<HTMLInputElement>(null);

  const { data: searchData, isFetching: searchLoading } = useSecuritySearch(tickerInput);
  const searchResults: MoexSecuritySearchItem[] = searchData?.body ?? [];

  // Close dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target as Node) &&
        tickerInputRef.current &&
        !tickerInputRef.current.contains(e.target as Node)
      ) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      ticker: '',
      type: 'BUY',
      securityType: 'STOCK',
      quantity: '' as unknown as number,
      price: '' as unknown as number,
      executedAt: '',
    },
  });

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      form.reset();
      setTickerInput('');
      setSelectedSecurityName(null);
      setShowDropdown(false);
    }
    onOpenChange?.(next);
  };

  const handleSelectSuggestion = (item: MoexSecuritySearchItem) => {
    form.setValue('ticker', item.ticker, { shouldValidate: true });
    form.setValue('securityType', item.securityType, { shouldValidate: true });
    setTickerInput(item.ticker);
    setSelectedSecurityName(item.name);
    setShowDropdown(false);
  };

  const handleSubmit = async (values: FormValues) => {
    try {
      await mutateAsync({
        ...values,
        executedAt: new Date(values.executedAt).toISOString(),
      });
      toast({ title: 'Сделка добавлена' });
      form.reset();
      setTickerInput('');
      setSelectedSecurityName(null);
      onOpenChange?.(false);
    } catch (error) {
      toast({
        title: 'Ошибка',
        description: error instanceof Error ? error.message : 'Не удалось добавить сделку',
        variant: 'destructive',
      });
    }
  };

  // Controlled mode: parent manages open state; uncontrolled: internal trigger button
  const isControlled = open !== undefined;

  return (
    <Dialog open={isControlled ? open : undefined} onOpenChange={handleOpenChange}>
      {!isControlled && (
        <DialogTrigger asChild>
          <Button className="bg-gradient-primary hover:opacity-90" size="sm">
            <Plus className="w-4 h-4 mr-2" />
            Добавить актив
          </Button>
        </DialogTrigger>
      )}
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Добавить сделку</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">

            {/* Ticker with autocomplete */}
            <FormField
              control={form.control}
              name="ticker"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-dashboard-text-muted">Тикер</FormLabel>
                  <FormControl>
                    <div className="relative">
                      <Input
                        ref={tickerInputRef}
                        placeholder="SBER"
                        className="bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
                        value={tickerInput}
                        onChange={(e) => {
                          const val = e.target.value;
                          setTickerInput(val);
                          field.onChange(val);
                          setSelectedSecurityName(null);
                          setShowDropdown(val.trim().length >= 2);
                        }}
                        onFocus={() => {
                          if (tickerInput.trim().length >= 2) setShowDropdown(true);
                        }}
                        autoComplete="off"
                      />
                      {/* Selected security name hint */}
                      {selectedSecurityName && (
                        <p className="text-xs text-dashboard-text-muted mt-1">{selectedSecurityName}</p>
                      )}
                      {/* Autocomplete dropdown */}
                      {showDropdown && (
                        <div
                          ref={dropdownRef}
                          className="absolute top-full left-0 right-0 bg-slate-800 border border-white/10 rounded-lg mt-1 z-50 max-h-48 overflow-y-auto"
                        >
                          {searchLoading && (
                            <div className="flex items-center gap-2 px-3 py-2 text-sm text-dashboard-text-muted">
                              <Loader2 className="w-3 h-3 animate-spin" />
                              <span>Поиск...</span>
                            </div>
                          )}
                          {!searchLoading && searchResults.length === 0 && tickerInput.trim().length >= 2 && (
                            <div className="px-3 py-2 text-sm text-dashboard-text-muted">
                              Ничего не найдено
                            </div>
                          )}
                          {!searchLoading && searchResults.map((item) => (
                            <button
                              key={`${item.ticker}-${item.boardId}`}
                              type="button"
                              className="w-full text-left px-3 py-2 text-sm hover:bg-white/10 transition-colors flex items-center justify-between gap-2"
                              onMouseDown={(e) => {
                                // Use mousedown to fire before blur
                                e.preventDefault();
                                handleSelectSuggestion(item);
                              }}
                            >
                              <div>
                                <span className="font-semibold text-dashboard-text font-mono">{item.ticker}</span>
                                <span className="text-dashboard-text-muted ml-2">{item.name}</span>
                              </div>
                              <span className="text-xs text-dashboard-text-muted shrink-0">
                                {item.securityType === 'STOCK' ? 'Акция' : 'Облигация'}
                              </span>
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* Type */}
            <FormField
              control={form.control}
              name="type"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-dashboard-text-muted">Тип сделки</FormLabel>
                  <Select onValueChange={field.onChange} defaultValue={field.value}>
                    <FormControl>
                      <SelectTrigger className="bg-white/5 border-white/10 text-dashboard-text">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="BUY">Покупка (BUY)</SelectItem>
                      <SelectItem value="SELL">Продажа (SELL)</SelectItem>
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* Security type */}
            <FormField
              control={form.control}
              name="securityType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-dashboard-text-muted">Тип инструмента</FormLabel>
                  <Select onValueChange={field.onChange} value={field.value}>
                    <FormControl>
                      <SelectTrigger className="bg-white/5 border-white/10 text-dashboard-text">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="STOCK">Акция (STOCK)</SelectItem>
                      <SelectItem value="BOND">Облигация (BOND)</SelectItem>
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid grid-cols-2 gap-4">
              {/* Quantity */}
              <FormField
                control={form.control}
                name="quantity"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel className="text-dashboard-text-muted">Количество</FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        placeholder="10"
                        min="0"
                        step="1"
                        className="bg-white/5 border-white/10 text-dashboard-text"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* Price */}
              <FormField
                control={form.control}
                name="price"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel className="text-dashboard-text-muted">Цена (₽)</FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        placeholder="285.50"
                        min="0"
                        step="0.01"
                        className="bg-white/5 border-white/10 text-dashboard-text"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            {/* Executed at */}
            <FormField
              control={form.control}
              name="executedAt"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-dashboard-text-muted">Дата и время сделки</FormLabel>
                  <FormControl>
                    <Input
                      type="datetime-local"
                      className="bg-white/5 border-white/10 text-dashboard-text"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="flex justify-end space-x-2 pt-2">
              <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
                Отмена
              </Button>
              <Button
                type="submit"
                disabled={isPending}
                className="bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20"
              >
                {isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                Добавить сделку
              </Button>
            </div>

          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
};

export default AddAssetDialog;
