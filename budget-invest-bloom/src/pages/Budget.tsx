import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Plus, Minus, DollarSign, Wallet, TrendingUp, Loader2 } from 'lucide-react';
import FinanceCard from '@/components/FinanceCard';
import { useState } from 'react';
import { useToast } from '@/hooks/use-toast';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useBudgetSummary } from '@/hooks/useBudgetSummary';
import { apiPost } from '@/lib/api';

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

  if (error) {
    toast({
      title: "Ошибка загрузки",
      description: summaryData?.message || "Не удалось загрузить данные бюджета",
      variant: "destructive",
    });
  }

  const summary = summaryData?.body;
  const income = summary?.income ?? 0;
  const expenses = summary?.expenses ?? 0;
  const balance = summary?.balance ?? 0;
  const capital = summary?.capital ?? 0;
  const personalInflation = summary?.personalInflation ?? 0;
  const categories = summary?.categories ?? [];
  const trends = summary?.trends;

  const parseTrend = (trendStr?: string) => {
    if (!trendStr) return { value: "0%", isPositive: true };
    return { value: trendStr, isPositive: trendStr.startsWith('+') };
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
        description: `Добавлен расход ${expenseForm.amount}₽`,
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
      description: `Добавлен доход ${incomeForm.amount}₽ из источника "${incomeForm.source}"`,
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
        description: `Добавлена категория "${categoryForm.name}" с бюджетом ${categoryForm.budget}₽`,
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
  };

  return (
    <div className="h-[calc(100vh-4rem)] bg-gradient-background overflow-hidden">
      <div className="max-w-7xl mx-auto p-6">

        {isLoading && (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="w-8 h-8 animate-spin text-primary" />
          </div>
        )}

        {/* Карточки обзора */}
        <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-6 mb-8">
          <FinanceCard
            title="Доходы"
            value={`₽${income.toLocaleString()}`}
            icon={<Plus className="w-5 h-5" />}
            trend={parseTrend(trends?.income)}
            gradient="success"
            onClick={() => navigate('/budget/metric/income')}
          />
          <FinanceCard
            title="Расходы"
            value={`₽${expenses.toLocaleString()}`}
            icon={<Minus className="w-5 h-5" />}
            trend={parseTrend(trends?.expenses)}
            gradient="secondary"
            onClick={() => navigate('/budget/metric/expenses')}
          />
          <FinanceCard
            title="Остаток"
            value={`₽${balance.toLocaleString()}`}
            icon={<DollarSign className="w-5 h-5" />}
            trend={parseTrend(trends?.balance)}
            gradient="primary"
            onClick={() => navigate('/budget/metric/balance')}
          />
          <FinanceCard
            title="Капитал"
            value={`₽${capital.toLocaleString()}`}
            icon={<Wallet className="w-5 h-5" />}
            trend={parseTrend(trends?.capital)}
            gradient="primary"
            onClick={() => navigate('/budget/metric/capital')}
          />
          <FinanceCard
            title="Личная инфляция"
            value={`${personalInflation}%`}
            icon={<TrendingUp className="w-5 h-5" />}
            trend={parseTrend(trends?.inflation)}
            gradient="secondary"
            onClick={() => navigate('/budget/metric/inflation')}
          />
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Категории расходов */}
          <Card className="shadow-card border-0">
          <CardHeader>
            <CardTitle className="flex items-center justify-between">
              <span>Категории расходов</span>
              <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
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
                  <DialogContent className="sm:max-w-[425px]">
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
                        // Форма добавления расхода
                        <>
                          <div className="grid gap-2">
                            <Label htmlFor="expense-amount">Сумма *</Label>
                            <Input
                              id="expense-amount"
                              type="number"
                              placeholder="0"
                              value={expenseForm.amount}
                              onChange={(e) => setExpenseForm(prev => ({...prev, amount: e.target.value}))}
                            />
                          </div>
                          <div className="grid gap-2">
                            <Label htmlFor="expense-category">Категория *</Label>
                            <Select 
                              value={expenseForm.category} 
                              onValueChange={(value) => setExpenseForm(prev => ({...prev, category: value}))}
                            >
                              <SelectTrigger>
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
                            <Label htmlFor="expense-description">Описание</Label>
                            <Input
                              id="expense-description"
                              placeholder="Описание расхода (необязательно)"
                              value={expenseForm.description}
                              onChange={(e) => setExpenseForm(prev => ({...prev, description: e.target.value}))}
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
                        // Форма добавления дохода
                        <>
                          <div className="grid gap-2">
                            <Label htmlFor="income-amount">Сумма *</Label>
                            <Input
                              id="income-amount"
                              type="number"
                              placeholder="0"
                              value={incomeForm.amount}
                              onChange={(e) => setIncomeForm(prev => ({...prev, amount: e.target.value}))}
                            />
                          </div>
                          <div className="grid gap-2">
                            <Label htmlFor="income-source">Источник *</Label>
                            <Select 
                              value={incomeForm.source} 
                              onValueChange={(value) => setIncomeForm(prev => ({...prev, source: value}))}
                            >
                              <SelectTrigger>
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
                            <Label htmlFor="income-description">Описание</Label>
                            <Input
                              id="income-description"
                              placeholder="Описание дохода (необязательно)"
                              value={incomeForm.description}
                              onChange={(e) => setIncomeForm(prev => ({...prev, description: e.target.value}))}
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
                              className="flex-1 bg-gradient-success hover:opacity-90"
                              onClick={handleAddIncome}
                            >
                              Добавить
                            </Button>
                          </div>
                        </>
                      ) : (
                        // Форма добавления категории
                        <>
                          <div className="grid gap-2">
                            <Label htmlFor="category-name">Название категории *</Label>
                            <Input
                              id="category-name"
                              placeholder="Название категории"
                              value={categoryForm.name}
                              onChange={(e) => setCategoryForm(prev => ({...prev, name: e.target.value}))}
                            />
                          </div>
                          <div className="grid gap-2">
                            <Label htmlFor="category-budget">Бюджет *</Label>
                            <Input
                              id="category-budget"
                              type="number"
                              placeholder="0"
                              value={categoryForm.budget}
                              onChange={(e) => setCategoryForm(prev => ({...prev, budget: e.target.value}))}
                            />
                          </div>
                          <div className="grid gap-2">
                            <Label htmlFor="category-emoji">Эмодзи</Label>
                            <Input
                              id="category-emoji"
                              placeholder="🛒"
                              maxLength={10}
                              value={categoryForm.emoji}
                              onChange={(e) => setCategoryForm(prev => ({...prev, emoji: e.target.value}))}
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
              </CardTitle>
              <div className="flex gap-3 mt-4">
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
            </CardHeader>
            <CardContent className="space-y-4 max-h-[calc(100vh-300px)] overflow-y-auto">
              {categories.map((category) => {
                const percentage = getProgressPercentage(category.amount, category.budget);
                const isOverBudget = category.amount > category.budget;

                return (
                  <div
                    key={category.id || category.name}
                    className="relative space-y-2 p-2 rounded-lg transition-all duration-300 hover:shadow-card hover:scale-105 cursor-pointer border border-transparent hover:border-border/20 overflow-hidden"
                    onClick={() => navigate(`/budget/category/${encodeURIComponent(category.name)}`)}
                  >
                    <div className="absolute inset-0 bg-gradient-primary opacity-5" />
                    <div className="relative z-10 flex justify-between items-center">
                      <div className="flex items-center space-x-3">
                        <span className="text-lg">{category.emoji}</span>
                        <span className="font-medium">{category.name}</span>
                      </div>
                      <div className="text-right">
                        <div className="font-semibold">
                          ₽{category.amount.toLocaleString()}
                        </div>
                        <div className="text-sm text-muted-foreground">
                          из ₽{category.budget.toLocaleString()}
                        </div>
                      </div>
                    </div>
                    <div className="relative z-10 w-full bg-muted rounded-full h-2">
                      <div
                        className={`h-2 rounded-full transition-all duration-500 ${
                          isOverBudget ? 'bg-destructive' : 'bg-gradient-primary'
                        }`}
                        style={{ width: `${percentage}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default Budget;