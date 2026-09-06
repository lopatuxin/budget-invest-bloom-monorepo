import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { OperationCategoryChips } from '@/components/operation/OperationCategoryChips';
import { OperationDatePicker } from '@/components/operation/OperationDatePicker';
import { useOperationCategories } from '@/hooks/useOperationCategories';
import { useCreateExpense } from '@/hooks/useCreateExpense';
import { useCreateIncome } from '@/hooks/useCreateIncome';
import { toApiDateString, daysFromToday } from '@/lib/dateOptions';
import { cn } from '@/lib/utils';
import type { OperationKind } from '@/types/budget';

const INCOME_SOURCES = [
  { value: 'SALARY', label: 'Зарплата' },
  { value: 'FREELANCE', label: 'Фриланс' },
  { value: 'INVESTMENTS', label: 'Инвестиции' },
  { value: 'GIFTS', label: 'Подарки' },
  { value: 'OTHER', label: 'Прочее' },
];

const AMOUNT_PATTERN = /^\d{1,13}(\.\d{1,2})?$/;
const AMOUNT_ERROR = 'Введите сумму больше нуля';

function parseAmount(raw: string): number | null {
  const normalized = raw.trim().replace(',', '.');
  if (!AMOUNT_PATTERN.test(normalized)) return null;
  const value = Number(normalized);
  return value > 0 ? value : null;
}

interface OperationFormProps {
  initialKind: OperationKind;
  onClose: () => void;
}

export function OperationForm({ initialKind, onClose }: OperationFormProps) {
  const [kind, setKind] = useState<OperationKind>(initialKind);
  const [amount, setAmount] = useState('');
  const [amountError, setAmountError] = useState<string | null>(null);
  const [categoryId, setCategoryId] = useState('');
  const [categoryError, setCategoryError] = useState<string | null>(null);
  const [source, setSource] = useState('');
  const [sourceError, setSourceError] = useState<string | null>(null);
  const [description, setDescription] = useState('');
  const [date, setDate] = useState<Date>(() => daysFromToday(0));

  const { data: categoriesData } = useOperationCategories();
  const categories = categoriesData?.body ?? [];
  const createExpense = useCreateExpense();
  const createIncome = useCreateIncome();
  const isPending = createExpense.isPending || createIncome.isPending;

  const resetErrors = () => {
    setAmountError(null);
    setCategoryError(null);
    setSourceError(null);
  };

  const handleSuccess = () => {
    setAmount('');
    setCategoryId('');
    setSource('');
    setDescription('');
    setDate(daysFromToday(0));
    resetErrors();
    onClose();
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    resetErrors();

    const parsedAmount = parseAmount(amount);
    if (parsedAmount === null) setAmountError(AMOUNT_ERROR);

    const missingCategory = kind === 'EXPENSE' && !categoryId;
    const missingSource = kind === 'INCOME' && !source;
    if (missingCategory) setCategoryError('Выберите категорию');
    if (missingSource) setSourceError('Выберите источник');

    if (parsedAmount === null || missingCategory || missingSource) return;

    const commonFields = {
      amount: parsedAmount,
      description: description.trim() || null,
      date: toApiDateString(date),
    };

    if (kind === 'EXPENSE') {
      createExpense.mutate({ ...commonFields, categoryId }, { onSuccess: handleSuccess });
    } else {
      createIncome.mutate({ ...commonFields, source }, { onSuccess: handleSuccess });
    }
  };

  const submitLabel = (() => {
    const parsedAmount = parseAmount(amount);
    return parsedAmount === null ? 'Записать' : `Записать ${parsedAmount.toLocaleString('ru-RU')} ₽`;
  })();

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div className="grid grid-cols-2 rounded-lg bg-app-surface-2 p-1">
        {(['EXPENSE', 'INCOME'] as const).map((option) => (
          <button
            key={option}
            type="button"
            aria-pressed={kind === option}
            onClick={() => {
              setKind(option);
              resetErrors();
            }}
            className={cn(
              'h-9 rounded-md text-sm font-medium transition-colors',
              kind === option ? 'bg-app-surface text-app-text shadow-sm' : 'text-app-text-muted'
            )}
          >
            {option === 'EXPENSE' ? 'Расход' : 'Доход'}
          </button>
        ))}
      </div>

      <div>
        <div className="relative">
          <Input
            type="text"
            inputMode="decimal"
            autoFocus
            placeholder="0"
            value={amount}
            onChange={(e) => {
              setAmount(e.target.value);
              setAmountError(null);
            }}
            className="h-14 lg:h-12 bg-app-surface border-app-border-strong text-app-text text-[30px] lg:text-2xl font-mono pr-10 ring-offset-app-surface"
          />
          <span className="absolute right-4 top-1/2 -translate-y-1/2 text-app-text-muted pointer-events-none">₽</span>
        </div>
        {amountError && <p className="mt-1 text-xs text-app-bad">{amountError}</p>}
      </div>

      {kind === 'EXPENSE' ? (
        categories.length === 0 ? (
          <p className="text-sm text-app-text-muted">
            Сначала создайте категорию.{' '}
            <Link to="/budget" onClick={onClose} className="text-app-accent underline">
              Перейти в бюджет
            </Link>
          </p>
        ) : (
          <div>
            <OperationCategoryChips
              options={categories.map((c) => ({ value: c.id, label: c.name, emoji: c.emoji }))}
              value={categoryId}
              onChange={(v) => {
                setCategoryId(v);
                setCategoryError(null);
              }}
            />
            {categoryError && <p className="mt-1.5 text-xs text-app-bad">{categoryError}</p>}
          </div>
        )
      ) : (
        <div>
          <OperationCategoryChips
            options={INCOME_SOURCES}
            value={source}
            onChange={(v) => {
              setSource(v);
              setSourceError(null);
            }}
          />
          {sourceError && <p className="mt-1.5 text-xs text-app-bad">{sourceError}</p>}
        </div>
      )}

      <Input
        type="text"
        placeholder="Описание"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        className="bg-app-surface border-app-border-strong text-app-text placeholder:text-app-text-dim ring-offset-app-surface"
      />

      <OperationDatePicker value={date} onChange={setDate} />

      <div className="flex gap-2 pt-1">
        <Button
          type="button"
          variant="outline"
          className="flex-1 h-[50px] lg:h-11 border-app-border-strong bg-app-surface text-app-text hover:bg-app-surface-2"
          onClick={onClose}
        >
          Отмена
        </Button>
        <Button
          type="submit"
          disabled={isPending}
          className="flex-1 h-[50px] lg:h-11 bg-app-accent text-app-accent-ink hover:bg-app-accent/90"
        >
          {isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
