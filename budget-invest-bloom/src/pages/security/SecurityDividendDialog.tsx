import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ru } from 'date-fns/locale';
import { CalendarDays, Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Calendar } from '@/components/ui/calendar';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { useToast } from '@/hooks/use-toast';
import { useCreateManualDividend } from '@/hooks/useCreateManualDividend';
import { ApiError } from '@/lib/api';
import { formatDayMonth, toApiDateString } from '@/lib/dateOptions';

const schema = z.object({
  recordDate: z.date({ required_error: 'Обязательное поле' }),
  paymentDate: z.date().optional(),
  // Empty input arrives as '' (untouched field stays undefined); both must
  // coerce to the same "Обязательное поле" message instead of Zod's raw
  // English "Expected number, received nan".
  amountPerShare: z.preprocess(
    (value) => (value === '' ? undefined : value),
    z.coerce.number({ invalid_type_error: 'Обязательное поле' }).positive('Должно быть > 0'),
  ),
});

type FormValues = z.infer<typeof schema>;

interface DividendDateFieldProps {
  label: string;
  value: Date | undefined;
  onChange: (date: Date | undefined) => void;
  placeholder: string;
}

// Shared by both date fields below — the required "record date" and the
// optional "payment date" differ only in label/placeholder/validation.
function DividendDateField({ label, value, onChange, placeholder }: DividendDateFieldProps) {
  const [open, setOpen] = useState(false);
  return (
    <FormItem className="flex flex-col">
      <FormLabel className="text-app-text-muted">{label}</FormLabel>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <FormControl>
            <Button
              type="button"
              variant="outline"
              className="w-full justify-start bg-app-surface border-app-border-strong text-app-text hover:bg-app-surface-2 font-normal"
            >
              <CalendarDays aria-hidden="true" className="w-4 h-4 mr-2 text-app-text-muted" />
              {value ? formatDayMonth(toApiDateString(value)) : <span className="text-app-text-dim">{placeholder}</span>}
            </Button>
          </FormControl>
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0 bg-app-surface border-app-border text-app-text" align="start">
          <Calendar
            mode="single"
            locale={ru}
            selected={value}
            onSelect={(date) => {
              onChange(date);
              setOpen(false);
            }}
            initialFocus
          />
        </PopoverContent>
      </Popover>
      <FormMessage />
    </FormItem>
  );
}

interface SecurityDividendDialogProps {
  ticker: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function SecurityDividendDialog({ ticker, open, onOpenChange }: SecurityDividendDialogProps) {
  const { toast } = useToast();
  const { mutateAsync, isPending } = useCreateManualDividend();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      recordDate: undefined as unknown as Date,
      paymentDate: undefined,
      amountPerShare: undefined as unknown as number,
    },
  });

  const handleOpenChange = (next: boolean) => {
    if (!next) form.reset();
    onOpenChange(next);
  };

  const handleSubmit = async (values: FormValues) => {
    try {
      await mutateAsync({
        ticker,
        recordDate: toApiDateString(values.recordDate),
        paymentDate: values.paymentDate ? toApiDateString(values.paymentDate) : undefined,
        amountPerShare: values.amountPerShare,
      });
      toast({ title: 'Дивиденд добавлен' });
      form.reset();
      onOpenChange(false);
    } catch (error) {
      if (error instanceof ApiError && error.code === 'DIVIDEND_EXISTS') {
        form.setError('recordDate', { message: 'На эту дату отсечки уже есть дивиденд' });
        return;
      }
      toast({
        title: 'Ошибка',
        description: error instanceof Error ? error.message : 'Не удалось добавить дивиденд',
        variant: 'destructive',
      });
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[400px] border-app-border text-app-text" style={{ background: 'rgb(var(--app-surface))' }}>
        <DialogHeader>
          <DialogTitle className="text-app-text">Добавить дивиденд вручную</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="recordDate"
              render={({ field }) => (
                <DividendDateField label="Дата отсечки" value={field.value} onChange={field.onChange} placeholder="Выберите дату" />
              )}
            />

            <FormField
              control={form.control}
              name="paymentDate"
              render={({ field }) => (
                <DividendDateField label="Дата выплаты" value={field.value} onChange={field.onChange} placeholder="Не указана" />
              )}
            />

            <FormField
              control={form.control}
              name="amountPerShare"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-app-text-muted">Сумма на акцию, ₽</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      placeholder="34.84"
                      min="0"
                      step="0.01"
                      className="bg-app-surface border-app-border-strong text-app-text ring-offset-app-surface"
                      {...field}
                      value={field.value ?? ''}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="flex justify-end space-x-2 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => handleOpenChange(false)}
                className="border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2"
              >
                Отмена
              </Button>
              <Button
                type="submit"
                disabled={isPending}
                className="bg-app-accent text-app-accent-ink hover:bg-app-accent/90"
              >
                {isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                Добавить
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
