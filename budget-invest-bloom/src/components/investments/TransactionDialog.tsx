import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { CalendarDays, Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Drawer, DrawerContent, DrawerHeader, DrawerTitle } from '@/components/ui/drawer';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { TransactionSecurityPicker } from '@/components/investments/TransactionSecurityPicker';
import { transactionSchema, type TransactionFormValues } from '@/components/investments/transactionSchema';
import { useIsMobile } from '@/hooks/use-mobile';
import { useToast } from '@/hooks/use-toast';
import { useCreateTransaction } from '@/hooks/useCreateTransaction';
import { useSecuritySnapshot } from '@/hooks/useSecuritySnapshot';
import { formatCurrency, formatQuantity, parseDecimalInput } from '@/lib/dateOptions';
import { formatTime } from '@/pages/investments/investmentsFormat';
import { cn } from '@/lib/utils';
import type { MoexSecuritySearchItem } from '@/types/investment';

interface TransactionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialSecurity?: MoexSecuritySearchItem;
  // The caller's open positions by ticker: a security picked from the list gets its
  // «в портфеле N шт» hint and oversell check whether it was preset or chosen later.
  quantityByTicker?: Record<string, number>;
}

function localDatetimeNow(): string {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

function defaultValues(initialSecurity?: MoexSecuritySearchItem): TransactionFormValues {
  return {
    ticker: initialSecurity?.ticker ?? '',
    securityType: initialSecurity?.securityType ?? 'STOCK',
    type: 'BUY',
    // '' rather than undefined — these render as controlled <Input>s from the first paint,
    // z.coerce.number() turns the string into a number on validate/submit.
    quantity: '' as unknown as number,
    price: '' as unknown as number,
    executedAt: localDatetimeNow(),
  };
}

// Shared by the investments page ("Сделка" button, no security preset) and the
// security page (opens with its own ticker already selected) — replaces AddAssetDialog.
export function TransactionDialog({ open, onOpenChange, initialSecurity, quantityByTicker }: TransactionDialogProps) {
  const isMobile = useIsMobile();
  const { toast } = useToast();
  const { mutateAsync, isPending } = useCreateTransaction();
  const { mutateAsync: fetchSnapshot, isPending: isFetchingSnapshot } = useSecuritySnapshot();

  const [security, setSecurity] = useState<MoexSecuritySearchItem | null>(initialSecurity ?? null);
  const [priceAsOf, setPriceAsOf] = useState<string | null>(null);

  const form = useForm<TransactionFormValues>({
    resolver: zodResolver(transactionSchema),
    defaultValues: defaultValues(initialSecurity),
  });

  const applyMarketPrice = async (item: MoexSecuritySearchItem) => {
    try {
      const result = await fetchSnapshot(item.ticker);
      const snapshotPrice = result?.body?.lastPrice ?? result?.body?.previousClose;
      if (snapshotPrice != null) {
        form.setValue('price', snapshotPrice, { shouldValidate: true });
        setPriceAsOf(result?.body?.fetchedAt ?? null);
      }
    } catch {
      // Silent fail — price field stays editable for manual input
    }
  };

  // Fresh form on every open, seeded from the caller — mirrors OperationDialogProvider
  // mounting OperationForm anew instead of an effect racing the first render.
  useEffect(() => {
    if (!open) return;
    setSecurity(initialSecurity ?? null);
    setPriceAsOf(null);
    form.reset(defaultValues(initialSecurity));
    if (initialSecurity) {
      void applyMarketPrice(initialSecurity);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, initialSecurity?.ticker]);

  const type = form.watch('type');
  const quantity = form.watch('quantity');
  const price = form.watch('price');

  const knownQuantity = security ? quantityByTicker?.[security.ticker] : undefined;

  const handleSelectSecurity = (item: MoexSecuritySearchItem) => {
    setSecurity(item);
    setPriceAsOf(null);
    form.setValue('ticker', item.ticker, { shouldValidate: true });
    form.setValue('securityType', item.securityType, { shouldValidate: true });
    void applyMarketPrice(item);
  };

  const handleSubmit = async (values: TransactionFormValues) => {
    if (!security) {
      form.setError('ticker', { message: 'Выберите бумагу из списка' });
      return;
    }
    if (values.type === 'SELL' && knownQuantity !== undefined && values.quantity > knownQuantity) {
      form.setError('quantity', { message: `в портфеле только ${formatQuantity(knownQuantity)} шт` });
      return;
    }
    try {
      await mutateAsync({
        ticker: values.ticker,
        type: values.type,
        securityType: values.securityType,
        quantity: values.quantity,
        price: values.price,
        executedAt: new Date(values.executedAt).toISOString(),
      });
      toast({ title: 'Сделка записана' });
      onOpenChange(false);
    } catch (error) {
      form.setError('quantity', { message: error instanceof Error ? error.message : 'Не удалось записать сделку' });
    }
  };

  // The quantity/price inputs are text fields (zod parses on submit), so their live watch()
  // value is still a raw string while the user types — parse first, or "+" below would
  // concatenate instead of add.
  const numericQuantity = parseDecimalInput(quantity) || 0;
  const numericPrice = parseDecimalInput(price) || 0;
  const amount = numericQuantity > 0 && numericPrice > 0 ? numericQuantity * numericPrice : null;
  const signedQuantity = type === 'BUY' ? numericQuantity : -numericQuantity;
  // An oversell has no "after" to show — the submit check reports it under the quantity field instead
  const afterQuantity =
    knownQuantity !== undefined && numericQuantity > 0 && knownQuantity + signedQuantity >= 0 ? knownQuantity + signedQuantity : null;

  const content = (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(handleSubmit)} className="flex flex-col gap-4">
        <FormField
          control={form.control}
          name="ticker"
          render={() => (
            <FormItem>
              <FormLabel className="text-app-text-muted">Бумага</FormLabel>
              <FormControl>
                <TransactionSecurityPicker value={security} onChange={handleSelectSecurity} />
              </FormControl>
              {knownQuantity !== undefined && (
                <p className="text-[11px] text-app-text-dim">в портфеле {formatQuantity(knownQuantity)} шт</p>
              )}
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="type"
          render={({ field }) => (
            <div role="radiogroup" aria-label="Вид сделки" className="flex gap-1 p-1 rounded-[10px] bg-app-track">
              {(['BUY', 'SELL'] as const).map((option) => (
                <button
                  key={option}
                  type="button"
                  role="radio"
                  aria-checked={field.value === option}
                  onClick={() => field.onChange(option)}
                  className={cn(
                    'flex-1 h-8 rounded-[7px] text-sm font-medium transition-colors',
                    field.value === option ? 'bg-app-surface text-app-text border border-app-border' : 'text-app-text-muted'
                  )}
                >
                  {option === 'BUY' ? 'Покупка' : 'Продажа'}
                </button>
              ))}
            </div>
          )}
        />

        <div className="grid grid-cols-2 gap-3">
          <FormField
            control={form.control}
            name="quantity"
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-app-text-muted">Количество, шт</FormLabel>
                <FormControl>
                  <Input
                    type="text"
                    inputMode="decimal"
                    className="font-mono bg-app-surface border-app-border-strong text-app-text ring-offset-app-surface"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="price"
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-app-text-muted">Цена за штуку, ₽</FormLabel>
                <FormControl>
                  <Input
                    type="text"
                    inputMode="decimal"
                    className="font-mono bg-app-surface border-app-border-strong text-app-text ring-offset-app-surface"
                    {...field}
                  />
                </FormControl>
                {security &&
                  (priceAsOf ? (
                    <p className="text-[11px] text-app-text-dim">
                      по бирже на {formatTime(priceAsOf)} ·{' '}
                      <button type="button" onClick={() => void applyMarketPrice(security)} className="text-app-accent underline">
                        подставить
                      </button>
                    </p>
                  ) : (
                    <p className="text-[11px] text-app-text-dim">
                      {isFetchingSnapshot ? 'загрузка цены...' : 'цена с биржи недоступна, введите вручную'}
                    </p>
                  ))}
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <FormField
          control={form.control}
          name="executedAt"
          render={({ field }) => (
            <FormItem>
              <FormLabel className="text-app-text-muted">Дата и время</FormLabel>
              <FormControl>
                <div className="relative">
                  <CalendarDays aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-app-text-dim pointer-events-none" />
                  <Input
                    type="datetime-local"
                    className="pl-9 font-mono bg-app-surface border-app-border-strong text-app-text ring-offset-app-surface"
                    {...field}
                  />
                </div>
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="border-t border-app-border pt-3 flex flex-col gap-1">
          <div className="flex justify-between text-sm">
            <span className="text-app-text-muted">Сумма сделки</span>
            <span className="font-mono font-semibold text-app-text">{amount !== null ? formatCurrency(amount) : '—'}</span>
          </div>
          {afterQuantity !== null && (
            <div className="flex justify-between text-xs text-app-text-dim">
              <span>После сделки</span>
              <span>{formatQuantity(afterQuantity)} шт</span>
            </div>
          )}
        </div>

        <div className="flex gap-2.5">
          <Button
            type="button"
            variant="outline"
            className="flex-1 border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2"
            onClick={() => onOpenChange(false)}
          >
            Отмена
          </Button>
          <Button type="submit" disabled={isPending} className="flex-1 bg-app-accent text-app-accent-ink hover:bg-app-accent/90">
            {isPending && <Loader2 aria-hidden="true" className="w-4 h-4 mr-2 animate-spin" />}
            {type === 'BUY' ? 'Записать покупку' : 'Записать продажу'}
          </Button>
        </div>
      </form>
    </Form>
  );

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        <DrawerContent className="bg-app-surface border-app-border text-app-text px-4 pb-6">
          <DrawerHeader>
            <DrawerTitle className="text-app-text">Сделка</DrawerTitle>
          </DrawerHeader>
          {content}
        </DrawerContent>
      </Drawer>
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[440px] border-app-border text-app-text" style={{ background: 'rgb(var(--app-surface))' }}>
        <DialogHeader>
          <DialogTitle className="flex items-baseline justify-between gap-3">
            <span className="font-display text-[26px] text-app-text">Сделка</span>
            <span className="text-app-text-dim text-xs font-normal">запишется и в бюджет как перевод</span>
          </DialogTitle>
        </DialogHeader>
        {content}
      </DialogContent>
    </Dialog>
  );
}
