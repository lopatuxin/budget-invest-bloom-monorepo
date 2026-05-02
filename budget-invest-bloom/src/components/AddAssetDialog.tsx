import { useState } from 'react';
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
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Command, CommandEmpty, CommandInput, CommandItem, CommandList } from '@/components/ui/command';
import { Plus, Loader2 } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { useCreateTransaction } from '@/hooks/useCreateTransaction';
import { useSecuritySearch } from '@/hooks/useSecuritySearch';
import { useSecurityList } from '@/hooks/useSecurityList';
import { useSecuritySnapshot } from '@/hooks/useSecuritySnapshot';
import type { MoexSecuritySearchItem } from '@/types/investment';
import { SecurityLogo } from '@/components/SecurityLogo';
import { SECURITY_TYPE_LABEL_SINGULAR } from '@/lib/securityType';

const schema = z.object({
  ticker: z.string().trim().min(1, 'Обязательное поле').max(16).transform(v => v.toUpperCase()),
  type: z.enum(['BUY', 'SELL']),
  securityType: z.enum(['STOCK', 'BOND', 'ETF', 'OFZ']),
  quantity: z.coerce.number().positive('Должно быть > 0'),
  price: z.coerce.number().positive('Должно быть > 0'),
  executedAt: z.string().min(1, 'Обязательное поле'),
});

type FormValues = z.infer<typeof schema>;

interface AddAssetDialogProps {
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

const AddAssetDialog = ({ open: dialogOpen, onOpenChange }: AddAssetDialogProps) => {
  const { toast } = useToast();
  const { mutateAsync, isPending } = useCreateTransaction();
  const { mutateAsync: fetchSnapshot } = useSecuritySnapshot();

  // Ticker autocomplete state
  const [tickerInput, setTickerInput] = useState('');
  const [open, setOpen] = useState(false); // popover open
  const [category, setCategory] = useState<'stocks' | 'bonds'>('stocks');
  const [selectedSecurity, setSelectedSecurity] = useState<MoexSecuritySearchItem | null>(null);

  const isSearchMode = tickerInput.trim().length >= 2;

  const { data: searchData, isFetching: searchLoading } = useSecuritySearch(
    tickerInput,
    category === 'stocks' ? 'STOCKS' : 'BONDS',
  );

  const { data: listData, isFetching: listLoading } = useSecurityList(
    category === 'stocks' ? 'STOCKS' : 'BONDS',
    open && !isSearchMode,
  );

  const allResults: MoexSecuritySearchItem[] = isSearchMode
    ? (searchData?.body ?? [])
    : (listData?.body ?? []);
  const isFetching = isSearchMode ? searchLoading : listLoading;

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      ticker: '',
      type: 'BUY',
      securityType: 'STOCK',
      quantity: undefined as unknown as number,
      price: undefined as unknown as number,
      executedAt: '',
    },
  });

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      form.reset();
      setTickerInput('');
      setOpen(false);
      setSelectedSecurity(null);
      setCategory('stocks');
    }
    onOpenChange?.(next);
  };

  const handleSelectSuggestion = async (item: MoexSecuritySearchItem) => {
    form.setValue('ticker', item.ticker, { shouldValidate: true });
    form.setValue('securityType', item.securityType, { shouldValidate: true });
    setTickerInput(item.ticker);
    setSelectedSecurity(item);
    setOpen(false);

    // Auto-fill executed date with current local datetime (no seconds, for datetime-local input)
    const now = new Date();
    const localIso = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
      .toISOString()
      .slice(0, 16);
    form.setValue('executedAt', localIso, { shouldValidate: true });

    // Auto-fill price from MOEX snapshot; silent fail — user can fill manually
    try {
      const result = await fetchSnapshot(item.ticker);
      const price = result?.body?.lastPrice ?? result?.body?.previousClose;
      if (price != null) {
        form.setValue('price', price, { shouldValidate: true });
      }
    } catch {
      // Silent fail: price field remains empty for manual input
    }
  };

  const handleSubmit = async (values: FormValues) => {
    if (!selectedSecurity) {
      form.setError('ticker', { message: 'Выберите бумагу из списка' });
      return;
    }
    try {
      await mutateAsync({
        ...values,
        executedAt: new Date(values.executedAt).toISOString(),
      });
      toast({ title: 'Сделка добавлена' });
      form.reset();
      setTickerInput('');
      setSelectedSecurity(null);
      setCategory('stocks');
      setOpen(false);
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
  const isControlled = dialogOpen !== undefined;

  return (
    <Dialog open={isControlled ? dialogOpen : undefined} onOpenChange={handleOpenChange}>
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

            {/* Security picker with Popover + Command combobox */}
            <FormField
              control={form.control}
              name="ticker"
              render={() => (
                <FormItem>
                  <FormLabel className="text-dashboard-text-muted">Бумага</FormLabel>
                  <FormControl>
                    <Popover open={open} onOpenChange={setOpen}>
                      <PopoverTrigger asChild>
                        <Button
                          variant="outline"
                          role="combobox"
                          className="w-full justify-start bg-white/5 border-white/10 text-dashboard-text hover:bg-white/10 font-normal"
                        >
                          {selectedSecurity ? (
                            <span className="flex items-center gap-2 min-w-0">
                              <SecurityLogo ticker={selectedSecurity.ticker} size={20} securityType={selectedSecurity.securityType} />
                              <span className="font-mono font-semibold">{selectedSecurity.ticker}</span>
                              <span className="text-dashboard-text-muted truncate">{selectedSecurity.name}</span>
                            </span>
                          ) : (
                            <span className="text-dashboard-text-muted">Выберите бумагу...</span>
                          )}
                        </Button>
                      </PopoverTrigger>
                      <PopoverContent className="w-[380px] p-0 bg-slate-800 border-white/10" align="start">
                        {/* Category tabs */}
                        <div role="tablist" className="flex border-b border-white/10">
                          <button
                            role="tab"
                            aria-selected={category === 'stocks'}
                            type="button"
                            onClick={() => setCategory('stocks')}
                            className={`flex-1 py-2 text-sm transition-colors ${
                              category === 'stocks'
                                ? 'bg-white/10 text-white'
                                : 'text-dashboard-text-muted hover:text-white'
                            }`}
                          >
                            Акции
                          </button>
                          <button
                            role="tab"
                            aria-selected={category === 'bonds'}
                            type="button"
                            onClick={() => setCategory('bonds')}
                            className={`flex-1 py-2 text-sm transition-colors ${
                              category === 'bonds'
                                ? 'bg-white/10 text-white'
                                : 'text-dashboard-text-muted hover:text-white'
                            }`}
                          >
                            Облигации
                          </button>
                        </div>
                        <Command shouldFilter={false}>
                          <CommandInput
                            placeholder="Поиск по тикеру или названию..."
                            value={tickerInput}
                            onValueChange={(val) => {
                              setTickerInput(val);
                              setSelectedSecurity(null);
                            }}
                            className="border-b border-white/10"
                          />
                          <CommandList
                            className="max-h-48"
                            onWheel={(e) => e.stopPropagation()}
                          >
                            {isFetching && (
                              <div className="flex items-center gap-2 px-3 py-3 text-sm text-dashboard-text-muted">
                                <Loader2 className="w-3 h-3 animate-spin" />
                                <span>Загрузка...</span>
                              </div>
                            )}
                            {!isFetching && allResults.length === 0 && (
                              <CommandEmpty>Ничего не найдено</CommandEmpty>
                            )}
                            {!isFetching && allResults.map((item) => (
                              <CommandItem
                                key={`${item.ticker}-${item.boardId}`}
                                value={item.ticker}
                                onSelect={() => handleSelectSuggestion(item)}
                                className="flex items-center gap-2 px-3 py-2 cursor-pointer hover:bg-white/10"
                              >
                                <SecurityLogo ticker={item.ticker} size={24} securityType={item.securityType} />
                                <span className="font-mono font-semibold text-dashboard-text">{item.ticker}</span>
                                <span className="text-dashboard-text-muted text-sm truncate flex-1">{item.name}</span>
                                <span className="text-xs text-dashboard-text-muted shrink-0">
                                  {SECURITY_TYPE_LABEL_SINGULAR[item.securityType] ?? item.securityType}
                                </span>
                              </CommandItem>
                            ))}
                          </CommandList>
                        </Command>
                      </PopoverContent>
                    </Popover>
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
