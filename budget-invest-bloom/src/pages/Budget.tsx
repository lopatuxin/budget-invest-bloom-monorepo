import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Plus, Minus, DollarSign, TrendingUp, TrendingDown, Loader2, ShoppingCart } from 'lucide-react';
import EmptyState from '@/components/EmptyState';
import { CategoryEmojiPicker } from '@/components/CategoryEmojiPicker';
import { useState, useEffect, useMemo, memo } from 'react';
import { useToast } from '@/hooks/use-toast';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useBudgetSummary } from '@/hooks/useBudgetSummary';
import { useCreateExpense } from '@/hooks/useCreateExpense';
import { useCreateIncome } from '@/hooks/useCreateIncome';
import { useCreateCategory } from '@/hooks/useCreateCategory';
import { formatCurrency, getYearOptions, MONTHS } from '@/lib/dateOptions';
import { DONUT_COLORS } from '@/lib/chartColors';
import { Skeleton } from '@/components/ui/skeleton';
import { useCountUp } from '@/hooks/useCountUp';

const DANGER_COLOR = '#EF4444';
const INCOME_SOURCES = [
  { value: 'SALARY',      label: 'Зарплата' },
  { value: 'FREELANCE',   label: 'Фриланс' },
  { value: 'INVESTMENTS', label: 'Инвестиции' },
  { value: 'GIFTS',       label: 'Подарки' },
  { value: 'OTHER',       label: 'Прочее' },
];


type DialogType = 'expense' | 'income' | 'category' | null;

type Category = {
  id: string;
  name: string;
  amount: number;
  budget: number;
  emoji: string;
};

interface CategoriesSectionProps {
  isLoading: boolean;
  categories: Category[];
  selectedMonth: string;
  selectedYear: string;
  onMonthChange: (v: string) => void;
  onYearChange: (v: string) => void;
  onCategoryClick: (name: string) => void;
  onCreateCategory: () => void;
}

const CategoriesSection = memo(({
  isLoading,
  categories,
  selectedMonth,
  selectedYear,
  onMonthChange,
  onYearChange,
  onCategoryClick,
  onCreateCategory,
}: CategoriesSectionProps) => {
  const getProgressPercentage = (amount: number, budget: number) => {
    if (budget === 0) return 0;
    return Math.min((amount / budget) * 100, 100);
  };

  const CARD_LIMIT = 8;
  const firstCards = categories.slice(0, CARD_LIMIT);
  const secondCards = categories.slice(CARD_LIMIT);

  const renderCategoryRow = (cat: Category, i: number) => (
    <button
      key={cat.id || cat.name}
      onClick={() => onCategoryClick(cat.name)}
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
        {cat.budget > 0 && (
          <div className="w-full bg-white/5 rounded-full h-1.5 mt-1.5">
            <div
              className="h-1.5 rounded-full animate-progress-grow"
              style={{
                width: `${Math.min(getProgressPercentage(cat.amount, cat.budget), 100)}%`,
                backgroundColor: cat.amount > cat.budget ? DANGER_COLOR : DONUT_COLORS[i % DONUT_COLORS.length],
                animationDelay: `${400 + i * 80}ms`,
              }}
            />
          </div>
        )}
      </div>
      <div className="text-right shrink-0">
        <p className="text-sm font-semibold text-dashboard-text font-mono">{formatCurrency(cat.amount)}</p>
        {cat.budget > 0 && (
          <p className="text-xs text-dashboard-text-muted font-mono">из {formatCurrency(cat.budget)}</p>
        )}
      </div>
    </button>
  );

  if (isLoading) {
    return <Skeleton className="h-[400px] lg:w-1/2" />;
  }

  return (
    <div className="flex flex-col lg:flex-row gap-4 lg:items-stretch">
      {/* First card - always shown, half-width only when second card exists */}
      <div className={`glass-card p-5 animate-fade-slide-up ${secondCards.length > 0 ? 'lg:flex-1' : 'lg:w-1/2'}`} style={{ animationDelay: '300ms' }}>
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold text-dashboard-text">Категории расходов</h3>
        </div>
        <div className="flex gap-3 mb-4">
          <Select value={selectedMonth} onValueChange={onMonthChange}>
            <SelectTrigger className="w-[140px] bg-white/5 border-white/10 text-dashboard-text hover:bg-white/[0.08] transition-colors">
              <SelectValue placeholder="Месяц" />
            </SelectTrigger>
            <SelectContent>
              {MONTHS.map((month) => (
                <SelectItem key={month.value} value={month.value}>
                  {month.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={selectedYear} onValueChange={onYearChange}>
            <SelectTrigger className="w-[100px] bg-white/5 border-white/10 text-dashboard-text hover:bg-white/[0.08] transition-colors">
              <SelectValue placeholder="Год" />
            </SelectTrigger>
            <SelectContent>
              {getYearOptions().map((year) => (
                <SelectItem key={year} value={year}>
                  {year}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        {categories.length === 0 ? (
          <EmptyState
            icon={<ShoppingCart className="w-10 h-10" />}
            title="Нет категорий"
            description="Создайте первую категорию для учёта расходов"
            actionLabel="Создать категорию"
            onAction={onCreateCategory}
          />
        ) : (
          <div className="space-y-2">
            {firstCards.map((cat, i) => renderCategoryRow(cat, i))}
          </div>
        )}
      </div>
      {/* Second card - shown only when there are overflow categories */}
      {secondCards.length > 0 && (
        <div className="glass-card p-5 lg:flex-1 animate-fade-slide-up" style={{ animationDelay: '360ms' }}>
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold text-dashboard-text">Остальные категории</h3>
          </div>
          <div className="space-y-2">
            {secondCards.map((cat, i) => renderCategoryRow(cat, CARD_LIMIT + i))}
          </div>
        </div>
      )}
    </div>
  );
});

const Budget = () => {
  const { toast } = useToast();
  const navigate = useNavigate();
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
  const createExpense = useCreateExpense();
  const createIncome = useCreateIncome();
  const createCategory = useCreateCategory();

  useEffect(() => {
    if (error) {
      toast({
        title: "Ошибка загрузки",
        description: error instanceof Error ? error.message : "Не удалось загрузить данные бюджета",
        variant: "destructive",
      });
    }
  }, [error, toast]);

  // Open dialog from query param — read searchParams inside the effect to avoid double-trigger
  useEffect(() => {
    const actionParam = searchParams.get('action');
    if (actionParam === 'expense' || actionParam === 'income' || actionParam === 'category') {
      setOpenDialog(actionParam);
      setSearchParams({}, { replace: true });
    }
  }, [searchParams, setSearchParams]);

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


  const handleAddExpense = () => {
    if (!expenseForm.amount || !expenseForm.category) {
      toast({ title: "Ошибка", description: "Заполните обязательные поля", variant: "destructive" });
      return;
    }
    const parsedAmount = parseFloat(expenseForm.amount);
    if (isNaN(parsedAmount) || parsedAmount < 0) {
      toast({ title: "Ошибка", description: "Введите корректную сумму", variant: "destructive" });
      return;
    }
    createExpense.mutate(
      { categoryId: expenseForm.category, amount: parsedAmount, description: expenseForm.description || null },
      { onSuccess: () => { setExpenseForm({ amount: '', category: '', description: ''  }); resetDialog(); } }
    );
  };

  const handleAddIncome = () => {
    if (!incomeForm.amount || !incomeForm.source) {
      toast({ title: "Ошибка", description: "Заполните обязательные поля", variant: "destructive" });
      return;
    }
    const parsedAmount = parseFloat(incomeForm.amount);
    if (isNaN(parsedAmount) || parsedAmount < 0) {
      toast({ title: "Ошибка", description: "Введите корректную сумму", variant: "destructive" });
      return;
    }
    createIncome.mutate(
      { amount: parsedAmount, source: incomeForm.source, description: incomeForm.description || null },
      { onSuccess: () => { setIncomeForm({ amount: '', source: '', description: ''  }); resetDialog(); } }
    );
  };

  const handleAddCategory = () => {
    if (!categoryForm.name) {
      toast({ title: "Ошибка", description: "Заполните обязательные поля", variant: "destructive" });
      return;
    }
    const budgetValue = categoryForm.budget ? Number(categoryForm.budget) : null;
    createCategory.mutate(
      { name: categoryForm.name, budget: budgetValue, emoji: categoryForm.emoji.trim() || undefined },
      { onSuccess: () => { setCategoryForm({ name: '', budget: '', emoji: ''  }); resetDialog(); } }
    );
  };

  const resetDialog = () => {
    setOpenDialog(null);
    setCategoryForm({ name: '', budget: '', emoji: '' });
    setExpenseForm({ amount: '', category: '', description: '' });
    setIncomeForm({ amount: '', source: '', description: '' });
  };

  // Animated KPI values — count-up from previous value (no flash to 0 on re-fetch)
  const animIncome = useCountUp(!isLoading && summary ? income : 0);
  const animExpenses = useCountUp(!isLoading && summary ? expenses : 0);
  const animBalance = useCountUp(!isLoading && summary ? balance : 0);
  const animInflation = useCountUp(!isLoading && summary ? personalInflation : 0);

  // Memoized to avoid rebuilding the array on every animation tick
  const kpiCards = useMemo(() => [
    { label: 'ДОХОДЫ', value: formatCurrency(animIncome), trend: trends?.income, icon: Plus, color: '#10B981', glow: 'rgba(16, 185, 129, 0.3)', path: '/budget/metric/income' },
    { label: 'РАСХОДЫ', value: formatCurrency(animExpenses), trend: trends?.expenses, icon: Minus, color: '#F59E0B', glow: 'rgba(245, 158, 11, 0.3)', path: '/budget/metric/expenses' },
    { label: 'СВОБОДНЫЕ СРЕДСТВА', value: formatCurrency(animBalance), trend: trends?.balance, icon: DollarSign, color: '#3B82F6', glow: 'rgba(59, 130, 246, 0.3)', path: '/budget/metric/balance' },
    { label: 'ЛИЧНАЯ ИНФЛЯЦИЯ', value: `${animInflation}%`, trend: trends?.inflation, icon: TrendingUp, color: '#EC4899', glow: 'rgba(236, 72, 153, 0.3)', path: '/budget/metric/inflation' },
  ], [animIncome, animExpenses, animBalance, animInflation, trends]);

  return (
    <div className="space-y-6 pb-6">
      {/* KPI Cards */}
      <div className="flex gap-4 overflow-x-auto snap-x snap-mandatory pb-2 -mx-4 px-4 lg:mx-0 lg:px-0 lg:grid lg:grid-cols-2 xl:grid-cols-4 lg:gap-5 lg:overflow-visible lg:pb-0 hide-scrollbar">
        {isLoading
          ? Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-[130px] min-w-[260px] snap-start lg:min-w-0" />)
          : kpiCards.map((card, index) => {
              const Icon = card.icon;
              const trend = parseTrend(card.trend);
              const isNegativeTrend = trend && !trend.isPositive;

              return (
                <div
                  key={card.label}
                  className="glass-card p-5 flex items-start justify-between group transition-all duration-300 hover:scale-[1.02] cursor-pointer animate-fade-slide-up min-w-[260px] snap-start lg:min-w-0"
                  onClick={() => navigate(card.path)}
                  style={{
                    borderLeft: `3px solid ${isNegativeTrend ? DANGER_COLOR : card.color}`,
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

      {/* Categories Section — isolated in React.memo to avoid re-render on every count-up tick */}
      <CategoriesSection
        isLoading={isLoading}
        categories={categories}
        selectedMonth={selectedMonth}
        selectedYear={selectedYear}
        onMonthChange={setSelectedMonth}
        onYearChange={setSelectedYear}
        onCategoryClick={(name) => navigate(`/budget/category/${encodeURIComponent(name)}`)}
        onCreateCategory={() => setOpenDialog('category')}
      />

      {/* Expense Dialog */}
      <Dialog open={openDialog === 'expense'} onOpenChange={(open) => { if (!open) resetDialog(); }}>
        <DialogContent className="sm:max-w-[425px]">
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
                className="flex-1"
                onClick={() => resetDialog()}
              >
                Отмена
              </Button>
              <Button
                className="flex-1 bg-amber-500/10 text-amber-400 hover:bg-amber-500/20"
                onClick={handleAddExpense}
                disabled={createExpense.isPending}
              >
                {createExpense.isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                Добавить
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Income Dialog */}
      <Dialog open={openDialog === 'income'} onOpenChange={(open) => { if (!open) resetDialog(); }}>
        <DialogContent className="sm:max-w-[425px]">
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
                  {INCOME_SOURCES.map((s) => (
                    <SelectItem key={s.value} value={s.value}>
                      {s.label}
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
                className="flex-1"
                onClick={() => resetDialog()}
              >
                Отмена
              </Button>
              <Button
                className="flex-1 bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20"
                onClick={handleAddIncome}
                disabled={createIncome.isPending}
              >
                {createIncome.isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                Добавить
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Category Dialog */}
      <Dialog open={openDialog === 'category'} onOpenChange={(open) => { if (!open) resetDialog(); }}>
        <DialogContent className="sm:max-w-[425px]">
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
              <Label htmlFor="category-budget" className="text-dashboard-text-muted">Бюджет</Label>
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
              <CategoryEmojiPicker
                value={categoryForm.emoji}
                onChange={(emoji) => setCategoryForm(prev => ({ ...prev, emoji }))}
              />
            </div>
            <div className="flex gap-2 pt-4">
              <Button
                variant="outline"
                className="flex-1"
                onClick={() => resetDialog()}
              >
                Отмена
              </Button>
              <Button
                className="flex-1 bg-blue-500/10 text-blue-400 hover:bg-blue-500/20"
                onClick={handleAddCategory}
                disabled={createCategory.isPending}
              >
                {createCategory.isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
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
