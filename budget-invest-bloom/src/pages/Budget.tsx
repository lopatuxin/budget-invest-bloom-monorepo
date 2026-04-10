import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Plus, Minus, DollarSign, Wallet, TrendingUp, TrendingDown, Loader2 } from 'lucide-react';
import { useState, useEffect } from 'react';
import { useToast } from '@/hooks/use-toast';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useBudgetSummary } from '@/hooks/useBudgetSummary';
import { apiPost } from '@/lib/api';

const formatCurrency = (value: number) => value.toLocaleString('ru-RU') + ' \u20BD';

const DONUT_COLORS = ['#10B981', '#3B82F6', '#F59E0B', '#8B5CF6', '#EC4899', '#06B6D4', '#F97316', '#14B8A6'];

const Skeleton = ({ className = '' }: { className?: string }) => (
  <div className={`animate-pulse bg-white/10 rounded-xl ${className}`} />
);

const Budget = () => {
  const { toast } = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const now = new Date();
  const [selectedMonth, setSelectedMonth] = useState(String(now.getMonth() + 1));
  const [selectedYear, setSelectedYear] = useState(String(now.getFullYear()));
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [selectedOperationType, setSelectedOperationType] = useState<'expense' | 'income' | 'category' | null>(null);
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

  const summary = summaryData?.body;
  const income = summary?.income ?? 0;
  const expenses = summary?.expenses ?? 0;
  const balance = summary?.balance ?? 0;
  const capital = summary?.capital ?? 0;
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
    setSelectedOperationType(null);
    setIsDialogOpen(false);
    setCategoryForm({ name: '', budget: '', emoji: '' });
    setExpenseForm({ amount: '', category: '', description: '' });
    setIncomeForm({ amount: '', source: '', description: '' });
  };

  const kpiCards = [
    { label: 'ДОХОДЫ', value: formatCurrency(income), trend: trends?.income, icon: Plus, color: '#10B981', glow: 'rgba(16, 185, 129, 0.3)', path: '/budget/metric/income' },
    { label: 'РАСХОДЫ', value: formatCurrency(expenses), trend: trends?.expenses, icon: Minus, color: '#F59E0B', glow: 'rgba(245, 158, 11, 0.3)', path: '/budget/metric/expenses' },
    { label: 'ОСТАТОК', value: formatCurrency(balance), trend: trends?.balance, icon: DollarSign, color: '#3B82F6', glow: 'rgba(59, 130, 246, 0.3)', path: '/budget/metric/balance' },
    { label: 'КАПИТАЛ', value: formatCurrency(capital), trend: trends?.capital, icon: Wallet, color: '#8B5CF6', glow: 'rgba(139, 92, 246, 0.3)', path: '/budget/metric/capital' },
    { label: 'ИНФЛЯЦИЯ', value: `${personalInflation}%`, trend: trends?.inflation, icon: TrendingUp, color: '#EC4899', glow: 'rgba(236, 72, 153, 0.3)', path: '/budget/metric/inflation' },
  ];

  return (
    <div className="space-y-6 pb-6">
      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-5">
        {isLoading
          ? Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-[130px]" />)
          : kpiCards.map(card => {
              const Icon = card.icon;
              const trend = parseTrend(card.trend);
              return (
                <div
                  key={card.label}
                  className="glass-card p-5 flex items-start justify-between group transition-all duration-300 hover:scale-[1.02] cursor-pointer"
                  onClick={() => navigate(card.path)}
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
        <div className="glass-card p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold text-dashboard-text">Категории расходов</h3>
            <Dialog open={isDialogOpen} onOpenChange={(open) => { if (!open) resetDialog(); else setIsDialogOpen(true); }}>
              <DialogTrigger asChild>
                <Button
                  size="sm"
                  className="bg-gradient-primary hover:opacity-90"
                  onClick={() => setSelectedOperationType('expense')}
                >
                  <Plus className="w-4 h-4 mr-2" />
                  Добавить
                </Button>
              </DialogTrigger>
              <DialogContent className="sm:max-w-[425px] bg-[#0B1929] border-white/10 text-dashboard-text">
                <DialogHeader>
                  <div className="flex flex-wrap gap-2 justify-center">
                    <Button
                      variant={selectedOperationType === 'expense' ? 'default' : 'outline'}
                      size="sm"
                      onClick={() => setSelectedOperationType('expense')}
                      className={selectedOperationType === 'expense' ? 'bg-gradient-primary hover:opacity-90' : ''}
                    >
                      <Minus className="w-4 h-4 mr-2" />
                      Расход
                    </Button>
                    <Button
                      variant={selectedOperationType === 'income' ? 'default' : 'outline'}
                      size="sm"
                      onClick={() => setSelectedOperationType('income')}
                      className={selectedOperationType === 'income' ? 'bg-gradient-success hover:opacity-90' : ''}
                    >
                      <Plus className="w-4 h-4 mr-2" />
                      Доход
                    </Button>
                    <Button
                      variant={selectedOperationType === 'category' ? 'default' : 'outline'}
                      size="sm"
                      onClick={() => setSelectedOperationType('category')}
                      className={selectedOperationType === 'category' ? 'bg-gradient-secondary hover:opacity-90' : ''}
                    >
                      <Plus className="w-4 h-4 mr-2" />
                      Категория
                    </Button>
                  </div>
                </DialogHeader>
                <div className="grid gap-4 py-4">
                  {selectedOperationType === 'expense' ? (
                    <>
                      <div className="grid gap-2">
                        <Label htmlFor="expense-amount" className="text-dashboard-text-muted">Сумма *</Label>
                        <Input
                          id="expense-amount"
                          type="number"
                          placeholder="0"
                          value={expenseForm.amount}
                          onChange={(e) => setExpenseForm(prev => ({...prev, amount: e.target.value}))}
                          className="bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
                        />
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
                    </>
                  ) : selectedOperationType === 'income' ? (
                    <>
                      <div className="grid gap-2">
                        <Label htmlFor="income-amount" className="text-dashboard-text-muted">Сумма *</Label>
                        <Input
                          id="income-amount"
                          type="number"
                          placeholder="0"
                          value={incomeForm.amount}
                          onChange={(e) => setIncomeForm(prev => ({...prev, amount: e.target.value}))}
                          className="bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
                        />
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
                    </>
                  ) : (
                    <>
                      <div className="grid gap-2">
                        <Label htmlFor="category-name" className="text-dashboard-text-muted">Название категории *</Label>
                        <Input
                          id="category-name"
                          placeholder="Название категории"
                          value={categoryForm.name}
                          onChange={(e) => setCategoryForm(prev => ({...prev, name: e.target.value}))}
                          className="bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
                        />
                      </div>
                      <div className="grid gap-2">
                        <Label htmlFor="category-budget" className="text-dashboard-text-muted">Бюджет *</Label>
                        <Input
                          id="category-budget"
                          type="number"
                          placeholder="0"
                          value={categoryForm.budget}
                          onChange={(e) => setCategoryForm(prev => ({...prev, budget: e.target.value}))}
                          className="bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
                        />
                      </div>
                      <div className="grid gap-2">
                        <Label htmlFor="category-emoji" className="text-dashboard-text-muted">Эмодзи</Label>
                        <Input
                          id="category-emoji"
                          placeholder="🛒"
                          maxLength={10}
                          value={categoryForm.emoji}
                          onChange={(e) => setCategoryForm(prev => ({...prev, emoji: e.target.value}))}
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
                          className="flex-1 bg-gradient-secondary hover:opacity-90"
                          onClick={handleAddCategory}
                          disabled={isCategorySubmitting}
                        >
                          {isCategorySubmitting && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                          Добавить
                        </Button>
                      </div>
                    </>
                  )}
                </div>
              </DialogContent>
            </Dialog>
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
                      className="h-1.5 rounded-full transition-all duration-500"
                      style={{
                        width: `${Math.min(getProgressPercentage(cat.amount, cat.budget), 100)}%`,
                        backgroundColor: cat.amount > cat.budget ? '#EF4444' : DONUT_COLORS[i % DONUT_COLORS.length],
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
    </div>
  );
};

export default Budget;
