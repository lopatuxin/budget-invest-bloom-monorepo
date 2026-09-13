import { z } from 'zod';
import { parseDecimalInput } from '@/lib/dateOptions';

const decimalField = (invalidMessage: string) =>
  z.preprocess(
    (value) => (typeof value === 'string' ? parseDecimalInput(value) : value),
    z.coerce.number({ invalid_type_error: invalidMessage }).positive('Должно быть больше 0'),
  );

export const transactionSchema = z.object({
  ticker: z.string().trim().min(1, 'Выберите бумагу из списка').max(16).transform((value) => value.toUpperCase()),
  securityType: z.enum(['STOCK', 'BOND', 'ETF', 'OFZ']),
  type: z.enum(['BUY', 'SELL']),
  quantity: decimalField('Введите количество'),
  price: decimalField('Введите цену'),
  executedAt: z.string().min(1, 'Обязательное поле'),
});

export type TransactionFormValues = z.infer<typeof transactionSchema>;
