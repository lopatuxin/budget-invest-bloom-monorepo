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
    if (!next) form.reset();
    onOpenChange?.(next);
  };

  const handleSubmit = async (values: FormValues) => {
    try {
      await mutateAsync({
        ...values,
        executedAt: new Date(values.executedAt).toISOString(),
      });
      toast({ title: 'Сделка добавлена' });
      form.reset();
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

            {/* Ticker */}
            <FormField
              control={form.control}
              name="ticker"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-dashboard-text-muted">Тикер</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="SBER"
                      className="bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
                      {...field}
                    />
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
                  <Select onValueChange={field.onChange} defaultValue={field.value}>
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
