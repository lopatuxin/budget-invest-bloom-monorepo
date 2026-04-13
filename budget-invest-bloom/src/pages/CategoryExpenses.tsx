import { CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from '@/components/ui/alert-dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ArrowLeft, Calendar, TrendingDown, Settings, Trash2, Wallet, BarChart3 } from 'lucide-react';
import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useToast } from '@/hooks/use-toast';
import { useCategoryAnalytics } from '@/hooks/useCategoryAnalytics';
import { useUpdateCategory } from '@/hooks/useUpdateCategory';
import { useDeleteCategory } from '@/hooks/useDeleteCategory';
import { useDeleteExpense } from '@/hooks/useDeleteExpense';
import { CategoryEmojiPicker } from '@/components/CategoryEmojiPicker';

const CategoryExpenses = () => {
  const { category } = useParams<{ category: string }>();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear().toString());
  const [selectedMonth, setSelectedMonth] = useState((new Date().getMonth() + 1).toString());
  const [chartPeriod, setChartPeriod] = useState<'month' | 'year'>('month');

  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const [isDeleteCategoryDialogOpen, setIsDeleteCategoryDialogOpen] = useState(false);
  const [cascadeConfirmState, setCascadeConfirmState] = useState<{ open: boolean; expenseCount: number }>({
    open: false,
    expenseCount: 0,
  });
  const [editCategoryName, setEditCategoryName] = useState(category || '');
  const [editCategoryLimit, setEditCategoryLimit] = useState('0');
  const [editCategoryEmoji, setEditCategoryEmoji] = useState('');

  const { data: analyticsResponse, isLoading } = useCategoryAnalytics(
    category || '',
    Number(selectedYear),
    Number(selectedMonth)
  );
  const updateCategoryMutation = useUpdateCategory();
  const deleteCategoryMutation = useDeleteCategory();
  const deleteExpenseMutation = useDeleteExpense();

  const analyticsData = analyticsResponse?.body;

  useEffect(() => {
    if (analyticsData) {
      setEditCategoryName(analyticsData.categoryName || category || '');
      setEditCategoryLimit(analyticsData.budget?.toString() || '0');
      setEditCategoryEmoji(analyticsData.emoji ?? '');
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
        emoji: editCategoryEmoji,
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

  const deleteCategoryRequest = (force: boolean) => {
    if (!analyticsData?.categoryId) return;

    deleteCategoryMutation.mutate(
      { categoryId: analyticsData.categoryId, force },
      {
        onSuccess: () => {
          setCascadeConfirmState({ open: false, expenseCount: 0 });
          setIsDeleteCategoryDialogOpen(false);
          setIsEditDialogOpen(false);
          navigate('/budget');
          toast({ title: 'Категория удалена' });
        },
        onError: (error: unknown) => {
          const message = error instanceof Error ? error.message : '';
          // Backend signals "has expenses" via 409 with count in parens
          const cascadeMatch = !force ? message.match(/есть связанные расходы \((\d+)\)/) : null;
          if (cascadeMatch) {
            const count = Number(cascadeMatch[1]);
            setIsDeleteCategoryDialogOpen(false);
            setCascadeConfirmState({ open: true, expenseCount: count });
            return;
          }
          toast({
            title: 'Ошибка',
            description: message || 'Не удалось удалить категорию',
            variant: 'destructive',
          });
        },
      }
    );
  };

  const handleDeleteCategory = () => deleteCategoryRequest(false);
  const handleCascadeDeleteCategory = () => deleteCategoryRequest(true);

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

            <Dialog open={isEditDialogOpen} onOpenChange={(open) => {
              if (open && analyticsData) {
                setEditCategoryName(analyticsData.categoryName || category || '');
                setEditCategoryLimit(analyticsData.budget?.toString() || '0');
                setEditCategoryEmoji(analyticsData.emoji ?? '');
              }
              setIsEditDialogOpen(open);
            }}>
              <DialogTrigger asChild>
                <Button variant="outline" size="sm" className="gap-2 border-white/10 text-dashboard-text hover:bg-white/5">
                  <Settings className="w-4 h-4" />
                  Редактировать
                </Button>
              </DialogTrigger>
              <DialogContent className="sm:max-w-[425px]">
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
                  <div className="grid gap-2">
                    <Label className="text-dashboard-text-muted">Эмодзи</Label>
                    <CategoryEmojiPicker value={editCategoryEmoji} onChange={setEditCategoryEmoji} />
                  </div>
                </div>
                <div className="flex justify-end gap-2">
                  <AlertDialog open={isDeleteCategoryDialogOpen} onOpenChange={setIsDeleteCategoryDialogOpen}>
                    <AlertDialogTrigger asChild>
                      <Button variant="destructive" className="mr-auto" disabled={deleteCategoryMutation.isPending}>
                        <Trash2 className="w-4 h-4 mr-2" />
                        Удалить
                      </Button>
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>Удалить категорию?</AlertDialogTitle>
                        <AlertDialogDescription>
                          Действие необратимо. Если у категории есть расходы, удаление будет заблокировано.
                        </AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel>Отмена</AlertDialogCancel>
                        <AlertDialogAction
                          onClick={handleDeleteCategory}
                          disabled={deleteCategoryMutation.isPending}
                          className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                        >
                          {deleteCategoryMutation.isPending ? 'Удаление...' : 'Удалить'}
                        </AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
                  <Button variant="outline" onClick={() => setIsEditDialogOpen(false)}>
                    Отмена
                  </Button>
                  <Button onClick={handleSaveCategory} disabled={updateCategoryMutation.isPending} className="bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20">
                    {updateCategoryMutation.isPending ? 'Сохранение...' : 'Сохранить'}
                  </Button>
                </div>
              </DialogContent>
            </Dialog>

            <AlertDialog
              open={cascadeConfirmState.open}
              onOpenChange={(open) => setCascadeConfirmState((s) => ({ ...s, open }))}
            >
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Удалить вместе с расходами?</AlertDialogTitle>
                  <AlertDialogDescription>
                    В категории {cascadeConfirmState.expenseCount === 1
                      ? '1 расход'
                      : `${cascadeConfirmState.expenseCount} расходов`}
                    . Удаление категории приведёт к удалению всех этих записей. Действие необратимо.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Отмена</AlertDialogCancel>
                  <AlertDialogAction
                    onClick={handleCascadeDeleteCategory}
                    disabled={deleteCategoryMutation.isPending}
                    className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                  >
                    {deleteCategoryMutation.isPending ? 'Удаление...' : 'Удалить всё'}
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
          <p className="text-dashboard-text-muted mt-2">
            Детальная аналитика расходов по категории
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-8">
          {/* total year card */}
          <div
            className="glass-card p-5 flex items-start justify-between"
            style={{ borderLeft: '3px solid #10B981' }}
          >
            <div className="space-y-2">
              <p className="text-[11px] uppercase tracking-widest text-dashboard-text-muted">Общая сумма за год</p>
              <p className="text-2xl font-bold font-mono text-dashboard-text">{(analyticsData?.totalYear ?? 0).toLocaleString()}</p>
            </div>
            <div
              className="w-11 h-11 rounded-xl flex items-center justify-center"
              style={{ backgroundColor: '#10B98120', boxShadow: '0 0 20px #10B98140' }}
            >
              <Wallet className="w-5 h-5" style={{ color: '#10B981' }} />
            </div>
          </div>

          {/* average year card */}
          <div
            className="glass-card p-5 flex items-start justify-between"
            style={{ borderLeft: '3px solid #0EA5E9' }}
          >
            <div className="space-y-2">
              <p className="text-[11px] uppercase tracking-widest text-dashboard-text-muted">Среднее за месяц</p>
              <p className="text-2xl font-bold font-mono text-dashboard-text">{(analyticsData?.averageYear ?? 0).toLocaleString()}</p>
            </div>
            <div
              className="w-11 h-11 rounded-xl flex items-center justify-center"
              style={{ backgroundColor: '#0EA5E920', boxShadow: '0 0 20px #0EA5E940' }}
            >
              <BarChart3 className="w-5 h-5" style={{ color: '#0EA5E9' }} />
            </div>
          </div>
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
                  variant={chartPeriod === 'month' ? 'default' : 'ghost'}
                  size="sm"
                  onClick={() => setChartPeriod('month')}
                  className="rounded-xl"
                >
                  По месяцам
                </Button>
                <Button
                  variant={chartPeriod === 'year' ? 'default' : 'ghost'}
                  size="sm"
                  onClick={() => setChartPeriod('year')}
                  className="rounded-xl"
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
