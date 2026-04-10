import { CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ArrowLeft, Calendar, TrendingDown, Settings, Trash2 } from 'lucide-react';
import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useToast } from '@/hooks/use-toast';
import { useCategoryAnalytics } from '@/hooks/useCategoryAnalytics';
import { useUpdateCategory } from '@/hooks/useUpdateCategory';
import { useDeleteExpense } from '@/hooks/useDeleteExpense';

const CategoryExpenses = () => {
  const { category } = useParams<{ category: string }>();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear().toString());
  const [selectedMonth, setSelectedMonth] = useState((new Date().getMonth() + 1).toString());
  const [chartPeriod, setChartPeriod] = useState<'month' | 'year'>('month');

  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const [editCategoryName, setEditCategoryName] = useState(category || '');
  const [editCategoryLimit, setEditCategoryLimit] = useState('0');

  const { data: analyticsResponse, isLoading } = useCategoryAnalytics(
    category || '',
    Number(selectedYear),
    Number(selectedMonth)
  );
  const updateCategoryMutation = useUpdateCategory();
  const deleteExpenseMutation = useDeleteExpense();

  const analyticsData = analyticsResponse?.body;

  useEffect(() => {
    if (analyticsData) {
      setEditCategoryName(analyticsData.categoryName || category || '');
      setEditCategoryLimit(analyticsData.budget?.toString() || '0');
    }
  }, [analyticsData]);

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

  const currentYear = new Date().getFullYear();
  const years = Array.from({ length: currentYear - 2020 + 2 }, (_, i) => String(2020 + i));

  const monthlyData = (analyticsData?.monthlyData || []).map(m => ({
    month: m.monthName,
    amount: m.amount,
  }));

  const yearlyData = (analyticsData?.yearlyData || []).map(y => ({
    year: String(y.year),
    amount: y.amount,
  }));

  const expenses = analyticsData?.expenses || [];
  const totalMonthExpenses = analyticsData?.totalExpenses || 0;

  const currentData = chartPeriod === 'month' ? monthlyData : yearlyData;

  const handleSaveCategory = () => {
    if (!analyticsData?.categoryId) return;

    updateCategoryMutation.mutate(
      {
        categoryId: analyticsData.categoryId,
        name: editCategoryName,
        budget: Number(editCategoryLimit),
      },
      {
        onSuccess: () => {
          toast({
            title: "Категория обновлена",
            description: `Название: ${editCategoryName}, Лимит: ${Number(editCategoryLimit).toLocaleString()}`,
          });
          setIsEditDialogOpen(false);
          navigate(`/budget/category/${encodeURIComponent(editCategoryName)}`, { replace: true });
        },
        onError: () => {
          toast({
            title: "Ошибка",
            description: "Не удалось обновить категорию",
            variant: "destructive",
          });
        },
      }
    );
  };

  const handleDeleteExpense = (id: string) => {
    deleteExpenseMutation.mutate(
      { expenseId: id },
      {
        onSuccess: () => {
          toast({
            title: "Расход удален",
            description: "Запись о расходе была успешно удалена",
          });
        },
        onError: () => {
          toast({
            title: "Ошибка",
            description: "Не удалось удалить расход",
            variant: "destructive",
          });
        },
      }
    );
  };

  if (isLoading) {
    return (
      <div className="h-[calc(100vh-4rem)] flex items-center justify-center">
        <p className="text-dashboard-text-muted text-lg">Загрузка...</p>
      </div>
    );
  }

  return (
    <div>
      <div className="max-w-7xl mx-auto p-6">
        <div className="mb-8">
          <Button
            variant="ghost"
            onClick={() => navigate('/budget')}
            className="mb-4 text-dashboard-text-muted hover:text-dashboard-text hover:bg-white/5"
          >
            <ArrowLeft className="w-4 h-4 mr-2" />
            Назад к бюджету
          </Button>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              {analyticsData?.emoji && (
                <span className="text-2xl">{analyticsData.emoji}</span>
              )}
              <h1 className="text-3xl font-bold text-dashboard-text">
                Категория: {analyticsData?.categoryName || category}
              </h1>
            </div>

            <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
              <DialogTrigger asChild>
                <Button variant="outline" size="sm" className="gap-2 border-white/10 text-dashboard-text hover:bg-white/5">
                  <Settings className="w-4 h-4" />
                  Редактировать
                </Button>
              </DialogTrigger>
              <DialogContent className="sm:max-w-[425px] bg-[#0B1929] border-white/10 text-dashboard-text">
                <DialogHeader>
                  <DialogTitle className="text-dashboard-text">Редактировать категорию</DialogTitle>
                </DialogHeader>
                <div className="grid gap-4 py-4">
                  <div className="grid grid-cols-4 items-center gap-4">
                    <Label htmlFor="category-name" className="text-right text-dashboard-text">
                      Название
                    </Label>
                    <Input
                      id="category-name"
                      value={editCategoryName}
                      onChange={(e) => setEditCategoryName(e.target.value)}
                      className="col-span-3 bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
                      placeholder="Название категории"
                    />
                  </div>
                  <div className="grid grid-cols-4 items-center gap-4">
                    <Label htmlFor="category-limit" className="text-right text-dashboard-text">
                      Лимит
                    </Label>
                    <Input
                      id="category-limit"
                      type="number"
                      value={editCategoryLimit}
                      onChange={(e) => setEditCategoryLimit(e.target.value)}
                      className="col-span-3 bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
                      placeholder="Лимит расходов"
                    />
                  </div>
                </div>
                <div className="flex justify-end gap-2">
                  <Button variant="outline" onClick={() => setIsEditDialogOpen(false)} className="border-white/10 text-dashboard-text hover:bg-white/5">
                    Отмена
                  </Button>
                  <Button onClick={handleSaveCategory} disabled={updateCategoryMutation.isPending} className="bg-emerald-500 hover:bg-emerald-600 text-white">
                    {updateCategoryMutation.isPending ? 'Сохранение...' : 'Сохранить'}
                  </Button>
                </div>
              </DialogContent>
            </Dialog>
          </div>
          <p className="text-dashboard-text-muted mt-2">
            Детальная аналитика расходов по категории
          </p>
        </div>

        <div className="glass-card mb-8">
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2 text-dashboard-text">
                <TrendingDown className="w-5 h-5" />
                График расходов
              </CardTitle>
              <div className="flex gap-2">
                <Button
                  variant={chartPeriod === 'month' ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => setChartPeriod('month')}
                  className={chartPeriod === 'month' ? 'bg-emerald-500 hover:bg-emerald-600 text-white' : 'border-white/10 text-dashboard-text-muted hover:bg-white/5'}
                >
                  По месяцам
                </Button>
                <Button
                  variant={chartPeriod === 'year' ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => setChartPeriod('year')}
                  className={chartPeriod === 'year' ? 'bg-emerald-500 hover:bg-emerald-600 text-white' : 'border-white/10 text-dashboard-text-muted hover:bg-white/5'}
                >
                  По годам
                </Button>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={currentData}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
                <XAxis
                  dataKey={chartPeriod === 'month' ? 'month' : 'year'}
                  tick={{ fill: '#94A3B8' }}
                />
                <YAxis tick={{ fill: '#94A3B8' }} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#0B1929',
                    border: '1px solid rgba(255,255,255,0.1)',
                    borderRadius: '0.75rem',
                    color: '#d6e3fa'
                  }}
                  formatter={(value) => [`${Number(value).toLocaleString()}`, 'Сумма']}
                />
                <Line
                  type="monotone"
                  dataKey="amount"
                  stroke="#10B981"
                  strokeWidth={3}
                  dot={{ fill: '#10B981', strokeWidth: 2, r: 6 }}
                  activeDot={{ r: 8, stroke: '#10B981' }}
                />
              </LineChart>
            </ResponsiveContainer>
          </CardContent>
        </div>

        <div className="glass-card">
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2 text-dashboard-text">
                <Calendar className="w-5 h-5" />
                Отчет по дням
                <span className="text-sm font-normal text-dashboard-text-muted ml-2 font-mono">
                  (Общая сумма: {totalMonthExpenses.toLocaleString()})
                </span>
              </CardTitle>
              <div className="flex gap-3">
                <Select value={selectedMonth} onValueChange={setSelectedMonth}>
                  <SelectTrigger className="w-[140px] bg-white/5 border-white/10 text-dashboard-text">
                    <SelectValue placeholder="Месяц" />
                  </SelectTrigger>
                  <SelectContent className="bg-[#0B1929] border-white/10">
                    {months.map((month) => (
                      <SelectItem key={month.value} value={month.value}>
                        {month.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Select value={selectedYear} onValueChange={setSelectedYear}>
                  <SelectTrigger className="w-[100px] bg-white/5 border-white/10 text-dashboard-text">
                    <SelectValue placeholder="Год" />
                  </SelectTrigger>
                  <SelectContent className="bg-[#0B1929] border-white/10">
                    {years.map((year) => (
                      <SelectItem key={year} value={year}>
                        {year}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-3 dashboard-scroll">
              {expenses.length === 0 && (
                <p className="text-center text-dashboard-text-muted py-8">
                  Нет расходов за выбранный период
                </p>
              )}
              {expenses.map((expense) => (
                <div
                  key={expense.id}
                  className="relative flex items-center justify-between p-4 rounded-lg border border-white/10 hover:border-white/20 transition-all duration-300 group overflow-hidden"
                >
                  <div className="absolute inset-0 bg-white/[0.03]" />
                  <div className="relative z-10 flex items-center gap-4">
                    <div className="flex flex-col">
                      <div className="font-medium text-dashboard-text">
                        {expense.description}
                      </div>
                      <div className="text-sm text-dashboard-text-muted">
                        {expense.date}
                      </div>
                    </div>
                  </div>
                  <div className="relative z-10 flex items-center gap-3">
                    <div className="font-semibold text-lg text-dashboard-text font-mono">
                      {expense.amount.toLocaleString()}
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="opacity-0 group-hover:opacity-100 transition-opacity duration-200 h-8 w-8 text-destructive hover:text-destructive hover:bg-destructive/10"
                      disabled={deleteExpenseMutation.isPending}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDeleteExpense(expense.id);
                      }}
                    >
                      <Trash2 className="w-4 h-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </div>
      </div>
    </div>
  );
};

export default CategoryExpenses;
