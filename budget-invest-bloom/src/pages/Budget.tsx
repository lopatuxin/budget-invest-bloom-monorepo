import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Plus, Minus, DollarSign, TrendingUp, TrendingDown, Loader2 } from 'lucide-react';
import { useState, useEffect } from 'react';
import { useToast } from '@/hooks/use-toast';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useBudgetSummary } from '@/hooks/useBudgetSummary';
import { apiPost } from '@/lib/api';

const formatCurrency = (value: number) => value.toLocaleString('ru-RU') + ' \u20BD';

// Animated count-up hook with easeOutCubic easing
const useCountUp = (target: number, duration = 800) => {
  const [value, setValue] = useState(0);
  useEffect(() => {
    if (target === 0) { setValue(0); return; }
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { setValue(target); return; }
    let rafId: number;
    const start = performance.now();
    const step = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(Math.round(target * eased));
      if (progress < 1) rafId = requestAnimationFrame(step);
    };
    rafId = requestAnimationFrame(step);
    return () => cancelAnimationFrame(rafId);
  }, [target, duration]);
  return value;
};

const DONUT_COLORS = ['#10B981', '#3B82F6', '#F59E0B', '#8B5CF6', '#EC4899', '#06B6D4', '#F97316', '#14B8A6'];

const EMOJI_OPTIONS = ['🛒', '🍽️', '🏠', '🚗', '💊', '🎓', '🎮', '👕', '✈️', '💰', '📱', '🎬', '🐱', '💡', '🎁', '💇'];

const Skeleton = ({ className = '' }: { className?: string }) => (
  <div className={`animate-pulse bg-white/10 rounded-xl ${className}`} />
);

type DialogType = 'expense' | 'income' | 'category' | null;

const Budget = () => {
  const { toast } = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const now = new Date();
  const [selectedMonth, setSelectedMonth] = useState(String(now.getMonth() + 1));
  const [selectedYear, setSelectedYear] = useState(String(now.getFullYear()));
  const [openDialog, setOpenDialog] = useState<DialogType>(null);
  const [expenseForm, setExpenseForm] = useState({
    amount: '',
    category: '',
    description: ''
  });
  const [incomeForm, setIncomeForm] = useState({
    amount: '',
    source: '',
    description: ''
  });
  const [categoryForm, setCategoryForm] = useState({
    name: '',
    budget: '',
    emoji: ''
  });

  const { data: summaryData, isLoading, error } = useBudgetSummary(selectedMonth, selectedYear);

  useEffect(() => {
    if (error) {
      toast({
        title: "Ошибка загрузки",
        description: error instanceof Error ? error.message : "Не удалось загрузить данные бюджета",
        variant: "destructive",
      });
    }
  }, [error, toast]);

  // Open dialog from query param (read once on mount / navigation)
  const actionParam = searchParams.get('action');
  useEffect(() => {
    if (actionParam === 'expense' || actionParam === 'income' || actionParam === 'category') {
      setOpenDialog(actionParam);
      setSearchParams({}, { replace: true });
    }
  }, [actionParam, setSearchParams]);

  const summary = summaryData?.body;
  const income = summary?.income ?? 0;
  const expenses = summary?.expenses ?? 0;
  const balance = summary?.balance ?? 0;
  const personalInflation = summary?.personalInflation ?? 0;
  const categories = summary?.categories ?? [];
  const trends = summary?.trends;

  const parseTrend = (trend: string | null | undefined) => {
    if (!trend) return null;
    const num = parseFloat(trend);
    if (isNaN(num)) return null;
    return { value: trend, isPositive: num >= 0 };
  };

  const months = [
    { value: '1', label: 'Январь' },
    { value: '2', label: 'Февраль' },
    { value: '3', label: 'Март' },
    { value: '4', label: 'Апрель' },
    { value: '5', label: 'Май' },
    { value: '6', label: 'Июнь' },
    { value: '7', label: 'Июль' },
    { value: '8', label: 'Август' },
    { value: '9', label: 'Сентябрь' },
    { value: '10', label: 'Октябрь' },
    { value: '11', label: 'Ноябрь' },
    { value: '12', label: 'Декабрь' },
  ];

  const years = Array.from(
    { length: new Date().getFullYear() - 2022 + 1 },
    (_, i) => String(2022 + i)
  );

  const getProgressPercentage = (amount: number, budget: number) => {
    if (budget === 0) return 0;
    return Math.min((amount / budget) * 100, 100);
  };

  const incomeSources = ['Зарплата', 'Фриланс', 'Инвестиции', 'Подарки', 'Прочее'];

  const [isExpenseSubmitting, setIsExpenseSubmitting] = useState(false);
  const [isCategorySubmitting, setIsCategorySubmitting] = useState(false);

  const handleAddExpense = async () => {
    if (!expenseForm.amount || !expenseForm.category) {
      toast({
        title: "Ошибка",
        description: "Заполните обязательные поля",
        variant: "destructive"
      });
      return;
    }

    setIsExpenseSubmitting(true);
    try {
      await apiPost('/api/budget/expenses', {
        categoryId: expenseForm.category,
        amount: parseFloat(expenseForm.amount),
        description: expenseForm.description || null,
      });

      toast({
        title: "Расход добавлен",
        description: `Добавлен расход ${expenseForm.amount}\u20BD`,
      });

      setExpenseForm({ amount: '', category: '', description: '' });
      queryClient.invalidateQueries({ queryKey: ['budget-summary'] });
      resetDialog();
    } catch (error) {
      const message = error instanceof Error ? error.message : "Не удалось добавить расход";
      toast({
        title: "Ошибка",
        description: message,
        variant: "destructive",
      });
    } finally {
      setIsExpenseSubmitting(false);
    }
  };

  const handleAddIncome = () => {
    if (!incomeForm.amount || !incomeForm.source) {
      toast({
        title: "Ошибка",
        description: "Заполните обязательные поля",
        variant: "destructive"
      });
      return;
    }

    toast({
      title: "Доход добавлен",
      description: `Добавлен доход ${incomeForm.amount}\u20BD из источника "${incomeForm.source}"`,
    });

    setIncomeForm({ amount: '', source: '', description: '' });
    resetDialog();
  };

  const handleAddCategory = async () => {
    if (!categoryForm.name || !categoryForm.budget) {
      toast({
        title: "Ошибка",
        description: "Заполните обязательные поля",
        variant: "destructive"
      });
      return;
    }

    const budgetValue = Number(categoryForm.budget);
    if (isNaN(budgetValue)) {
      toast({ title: "Ошибка", description: "Некорректное значение бюджета", variant: "destructive" });
      return;
    }

    setIsCategorySubmitting(true);
    try {
      await apiPost('/api/budget/categories', {
        name: categoryForm.name,
        budget: budgetValue,
        emoji: categoryForm.emoji.trim() || undefined,
      });

      toast({
        title: "Категория добавлена",
        description: `Добавлена категория "${categoryForm.name}" с бюджетом ${categoryForm.budget}\u20BD`,
      });

      setCategoryForm({ name: '', budget: '', emoji: '' });
      queryClient.invalidateQueries({ queryKey: ['budget-summary'] });
      resetDialog();
    } catch (error) {
      const message = error instanceof Error ? error.message : "Не удалось добавить категорию";
      toast({
        title: "Ошибка",
        description: message,
        variant: "destructive",
      });
    } finally {
      setIsCategorySubmitting(false);
    }
  };

  const resetDialog = () => {
    setOpenDialog(null);
    setCategoryForm({ name: '', budget: '', emoji: '' });
    setExpenseForm({ amount: '', category: '', description: '' });
    setIncomeForm({ amount: '', source: '', description: '' });
  };

  // Animated KPI values (count-up from 0)
  const animIncome = useCountUp(!isLoading && summary ? income : 0);
  const animExpenses = useCountUp(!isLoading && summary ? expenses : 0);
  const animBalance = useCountUp(!isLoading && summary ? balance : 0);
  const animInflation = useCountUp(!isLoading && summary ? personalInflation : 0);

  const kpiCards = [
    { label: 'ДОХОДЫ', value: formatCurrency(animIncome), trend: trends?.income, icon: Plus, color: '#10B981', glow: 'rgba(16, 185, 129, 0.3)', path: '/budget/metric/income' },
    { label: 'РАСХОДЫ', value: formatCurrency(animExpenses), trend: trends?.expenses, icon: Minus, color: '#F59E0B', glow: 'rgba(245, 158, 11, 0.3)', path: '/budget/metric/expenses' },
    { label: 'СВОБОДНЫЕ СРЕДСТВА', value: formatCurrency(animBalance), trend: trends?.balance, icon: DollarSign, color: '#3B82F6', glow: 'rgba(59, 130, 246, 0.3)', path: '/budget/metric/balance' },
    { label: 'ЛИЧНАЯ ИНФЛЯЦИЯ', value: `${animInflation}%`, trend: trends?.inflation, icon: TrendingUp, color: '#EC4899', glow: 'rgba(236, 72, 153, 0.3)', path: '/budget/metric/inflation' },
  ];

  return (
    <div className="space-y-6 pb-6">
      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-5">
        {isLoading
          ? Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-[130px]" />)
          : kpiCards.map((card, index) => {
              const Icon = card.icon;
              const trend = parseTrend(card.trend);
              const isNegativeTrend = trend && !trend.isPositive;

              return (
                <div
                  key={card.label}
                  className="glass-card p-5 flex items-start justify-between group transition-all duration-300 hover:scale-[1.02] cursor-pointer animate-fade-slide-up"
                  onClick={() => navigate(card.path)}
                  style={{
                    borderLeft: `3px solid ${isNegativeTrend ? '#EF4444' : card.color}`,
                    animationDelay: `${index * 60}ms`,
                  }}
                >
                  <div className="space-y-2">
                    <p className="text-[11px] font-semibold tracking-widest text-dashboard-text-muted">
                      {card.label}
                    </p>
                    <p className="text-2xl font-bold text-dashboard-text font-mono">{card.value}</p>
                    {trend && (
                      <div className={`flex items-center gap-1 text-xs font-medium ${trend.isPositive ? 'text-emerald-400' : 'text-red-400'}`}>
                        {trend.isPositive ? <TrendingUp className="w-3.5 h-3.5" /> : <TrendingDown className="w-3.5 h-3.5" />}
                        <span className="font-mono">{trend.value}</span>
                      </div>
                    )}
                  </div>
                  <div
                    className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0"
                    style={{ backgroundColor: `${card.color}20`, boxShadow: `0 0 20px ${card.glow}` }}
                  >
                    <Icon className="w-5 h-5" style={{ color: card.color }} />
                  </div>
                </div>
              );
            })}
      </div>

      {/* Categories Section */}
      {isLoading ? (
        <Skeleton className="h-[400px]" />
      ) : (
        <div className="glass-card p-5 animate-fade-slide-up" style={{ animationDelay: '300ms' }}>
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold text-dashboard-text">Категории расходов</h3>
          </div>
          <div className="flex gap-3 mb-4">
            <Select value={selectedMonth} onValueChange={setSelectedMonth}>
              <SelectTrigger className="w-[140px]">
                <SelectValue placeholder="Месяц" />
              </SelectTrigger>
              <SelectContent>
                {months.map((month) => (
                  <SelectItem key={month.value} value={month.value}>
                    {month.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={selectedYear} onValueChange={setSelectedYear}>
              <SelectTrigger className="w-[100px]">
                <SelectValue placeholder="Год" />
              </SelectTrigger>
              <SelectContent>
                {years.map((year) => (
                  <SelectItem key={year} value={year}>
                    {year}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2 max-h-[400px] overflow-y-auto dashboard-scroll pr-1">
            {categories.map((cat, i) => (
              <button
                key={cat.id || cat.name}
                onClick={() => navigate(`/budget/category/${encodeURIComponent(cat.name)}`)}
                className="w-full flex items-center gap-4 px-4 py-3 rounded-xl bg-white/[0.03] hover:bg-white/[0.07] transition-all duration-200 text-left group"
              >
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center text-lg shrink-0"
                  style={{ backgroundColor: `${DONUT_COLORS[i % DONUT_COLORS.length]}20` }}
                >
                  {cat.emoji}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-dashboard-text truncate">{cat.name}</p>
                  <div className="w-full bg-white/5 rounded-full h-1.5 mt-1.5">
                    <div
                      className="h-1.5 rounded-full animate-progress-grow"
                      style={{
                        width: `${Math.min(getProgressPercentage(cat.amount, cat.budget), 100)}%`,
                        backgroundColor: cat.amount > cat.budget ? '#EF4444' : DONUT_COLORS[i % DONUT_COLORS.length],
                        animationDelay: `${400 + i * 80}ms`,
                      }}
                    />
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <p className="text-sm font-semibold text-dashboard-text font-mono">{formatCurrency(cat.amount)}</p>
                  <p className="text-xs text-dashboard-text-muted font-mono">из {formatCurrency(cat.budget)}</p>
                </div>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Expense Dialog */}
      <Dialog open={openDialog === 'expense'} onOpenChange={(open) => { if (!open) resetDialog(); }}>
        <DialogContent className="sm:max-w-[425px] bg-[#0B1929] border-white/10 text-dashboard-text">
          <DialogHeader>
            <DialogTitle>Добавить расход</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="expense-amount" className="text-dashboard-text-muted">Сумма *</Label>
              <div className="relative">
                <Input
                  id="expense-amount"
                  type="number"
                  placeholder="0"
                  autoFocus
                  value={expenseForm.amount}
                  onChange={(e) => setExpenseForm(prev => ({...prev, amount: e.target.value}))}
                  className="bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted pr-8"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-dashboard-text-muted text-sm pointer-events-none">&#8381;</span>
              </div>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="expense-category" className="text-dashboard-text-muted">Категория *</Label>
              <Select
                value={expenseForm.category}
                onValueChange={(value) => setExpenseForm(prev => ({...prev, category: value}))}
              >
                <SelectTrigger className="bg-white/5 border-white/10 text-dashboard-text">
                  <SelectValue placeholder="Выберите категорию" />
                </SelectTrigger>
                <SelectContent>
                  {categories.map((category) => (
                    <SelectItem key={category.id} value={category.id}>
                      {category.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="expense-description" className="text-dashboard-text-muted">Описание</Label>
              <Input
                id="expense-description"
                placeholder="Описание расхода (необязательно)"
                value={expenseForm.description}
                onChange={(e) => setExpenseForm(prev => ({...prev, description: e.target.value}))}
                className="bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
              />
            </div>
            <div className="flex gap-2 pt-4">
              <Button
                variant="outline"
                className="flex-1 border-white/10 text-dashboard-text hover:bg-white/5"
                onClick={() => resetDialog()}
              >
                Отмена
              </Button>
              <Button
                className="flex-1 bg-gradient-primary hover:opacity-90"
                onClick={handleAddExpense}
                disabled={isExpenseSubmitting}
              >
                {isExpenseSubmitting && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                Добавить
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Income Dialog */}
      <Dialog open={openDialog === 'income'} onOpenChange={(open) => { if (!open) resetDialog(); }}>
        <DialogContent className="sm:max-w-[425px] bg-[#0B1929] border-white/10 text-dashboard-text">
          <DialogHeader>
            <DialogTitle>Добавить доход</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="income-amount" className="text-dashboard-text-muted">Сумма *</Label>
              <div className="relative">
                <Input
                  id="income-amount"
                  type="number"
                  placeholder="0"
                  autoFocus
                  value={incomeForm.amount}
                  onChange={(e) => setIncomeForm(prev => ({...prev, amount: e.target.value}))}
                  className="bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted pr-8"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-dashboard-text-muted text-sm pointer-events-none">&#8381;</span>
              </div>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="income-source" className="text-dashboard-text-muted">Источник *</Label>
              <Select
                value={incomeForm.source}
                onValueChange={(value) => setIncomeForm(prev => ({...prev, source: value}))}
              >
                <SelectTrigger className="bg-white/5 border-white/10 text-dashboard-text">
                  <SelectValue placeholder="Выберите источник" />
                </SelectTrigger>
                <SelectContent>
                  {incomeSources.map((source) => (
                    <SelectItem key={source} value={source}>
                      {source}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="income-description" className="text-dashboard-text-muted">Описание</Label>
              <Input
                id="income-description"
                placeholder="Описание дохода (необязательно)"
                value={incomeForm.description}
                onChange={(e) => setIncomeForm(prev => ({...prev, description: e.target.value}))}
                className="bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
              />
            </div>
            <div className="flex gap-2 pt-4">
              <Button
                variant="outline"
                className="flex-1 border-white/10 text-dashboard-text hover:bg-white/5"
                onClick={() => resetDialog()}
              >
                Отмена
              </Button>
              <Button
                className="flex-1 bg-gradient-success hover:opacity-90"
                onClick={handleAddIncome}
              >
                Добавить
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Category Dialog */}
      <Dialog open={openDialog === 'category'} onOpenChange={(open) => { if (!open) resetDialog(); }}>
        <DialogContent className="sm:max-w-[425px] bg-[#0B1929] border-white/10 text-dashboard-text">
          <DialogHeader>
            <DialogTitle>Новая категория</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="category-name" className="text-dashboard-text-muted">Название категории *</Label>
              <Input
                id="category-name"
                placeholder="Название категории"
                autoFocus
                value={categoryForm.name}
                onChange={(e) => setCategoryForm(prev => ({...prev, name: e.target.value}))}
                className="bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="category-budget" className="text-dashboard-text-muted">Бюджет *</Label>
              <div className="relative">
                <Input
                  id="category-budget"
                  type="number"
                  placeholder="0"
                  value={categoryForm.budget}
                  onChange={(e) => setCategoryForm(prev => ({...prev, budget: e.target.value}))}
                  className="bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted pr-8"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-dashboard-text-muted text-sm pointer-events-none">&#8381;</span>
              </div>
            </div>
            <div className="grid gap-2">
              <Label className="text-dashboard-text-muted">Эмодзи</Label>
              <div className="grid grid-cols-8 gap-1.5">
                {EMOJI_OPTIONS.map((emoji) => (
                  <button
                    key={emoji}
                    type="button"
                    aria-label={`Выбрать эмодзи ${emoji}`}
                    aria-pressed={categoryForm.emoji === emoji}
                    onClick={() => setCategoryForm(prev => ({...prev, emoji}))}
                    className={`w-9 h-9 rounded-lg text-lg flex items-center justify-center transition-all duration-150 ${
                      categoryForm.emoji === emoji
                        ? 'bg-white/15 ring-1 ring-white/30'
                        : 'hover:bg-white/10'
                    }`}
                  >
                    {emoji}
                  </button>
                ))}
              </div>
            </div>
            <div className="flex gap-2 pt-4">
              <Button
                variant="outline"
                className="flex-1 border-white/10 text-dashboard-text hover:bg-white/5"
                onClick={() => resetDialog()}
              >
                Отмена
              </Button>
              <Button
                className="flex-1 bg-gradient-secondary hover:opacity-90"
                onClick={handleAddCategory}
                disabled={isCategorySubmitting}
              >
                {isCategorySubmitting && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                Добавить
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default Budget;
