import { useState } from 'react';
import { Minus, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { parseDecimalInput, pluralize } from '@/lib/dateOptions';
import type { ProjectionRequest } from '@/types/investment';

interface ForecastFormProps {
  onSubmit: (request: ProjectionRequest) => void;
  isPending: boolean;
}

// Rubles with up to kopecks (digit groups may be pasted with spaces), and a percentage with the
// 0,1 step (p.20); either decimal separator.
const DEPOSIT_INPUT = /^[\d\s]*([.,]\d{0,2})?$/;
const WITHDRAWAL_INPUT = /^\d{0,3}([.,]\d?)?$/;

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function isWithdrawalInput(text: string): boolean {
  return WITHDRAWAL_INPUT.test(text) && (parseDecimalInput(text) || 0) <= 100;
}

// Parameters card: years/deposit/withdrawal-rate inputs plus the "Рассчитать" button that
// fires the request — useProjection only queries once a request has been submitted (p.20).
// The two number fields keep the raw text while typing: a number state would turn "0," into 0
// on every keystroke and make the field impossible to clear.
export function ForecastForm({ onSubmit, isPending }: ForecastFormProps) {
  const [years, setYears] = useState(10);
  const [monthlyDepositText, setMonthlyDepositText] = useState('0');
  const [withdrawalPercentText, setWithdrawalPercentText] = useState('0');

  const handleSubmit = () => {
    const withdrawalPercent = clamp(parseDecimalInput(withdrawalPercentText) || 0, 0, 100);
    onSubmit({
      horizonMonths: years * 12,
      monthlyDeposit: parseDecimalInput(monthlyDepositText) || 0,
      withdrawalRatePerYear: withdrawalPercent / 100,
      overrides: {},
    });
  };

  return (
    <div className="glass-card p-5 grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-[1fr_1fr_1fr_auto] gap-4 items-end">
      <div className="flex flex-col gap-1.5">
        <label className="text-app-text-muted text-[13px]">Горизонт</label>
        <div className="h-10 flex items-center justify-between rounded-lg border border-app-border-strong bg-app-surface px-3">
          <span className="font-mono text-app-text text-sm">{pluralize(years, ['год', 'года', 'лет'])}</span>
          <div className="flex gap-1.5">
            <button
              type="button"
              aria-label="Уменьшить горизонт"
              onClick={() => setYears((value) => clamp(value - 1, 1, 30))}
              className="w-[26px] h-[26px] rounded-lg border border-app-border-strong flex items-center justify-center text-app-text-dim hover:bg-app-surface-2"
            >
              <Minus aria-hidden="true" className="w-3.5 h-3.5" />
            </button>
            <button
              type="button"
              aria-label="Увеличить горизонт"
              onClick={() => setYears((value) => clamp(value + 1, 1, 30))}
              className="w-[26px] h-[26px] rounded-lg border border-app-border-strong flex items-center justify-center text-app-text-dim hover:bg-app-surface-2"
            >
              <Plus aria-hidden="true" className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-app-text-muted text-[13px]">Пополнение в месяц</label>
        <Input
          type="text"
          inputMode="decimal"
          aria-label="Пополнение в месяц, рублей"
          value={monthlyDepositText}
          onChange={(event) => DEPOSIT_INPUT.test(event.target.value) && setMonthlyDepositText(event.target.value)}
          className="h-10 font-mono bg-app-surface border-app-border-strong text-app-text ring-offset-app-surface"
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label className="text-app-text-muted text-[13px]">Изъятие в год</label>
        <div className="h-10 flex items-center justify-between rounded-lg border border-app-border-strong bg-app-surface px-3">
          <input
            type="text"
            inputMode="decimal"
            aria-label="Изъятие в год, процентов"
            value={withdrawalPercentText}
            onChange={(event) => isWithdrawalInput(event.target.value) && setWithdrawalPercentText(event.target.value)}
            className="w-14 font-mono bg-transparent text-app-text text-sm outline-none"
          />
          <span className="text-app-text-dim text-[11px]">от стоимости портфеля</span>
        </div>
      </div>

      <Button onClick={handleSubmit} disabled={isPending} className="h-10 bg-app-accent text-app-accent-ink hover:bg-app-accent/90">
        {isPending ? 'Расчёт...' : 'Рассчитать'}
      </Button>
    </div>
  );
}
