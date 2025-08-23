import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ArrowLeft, Calendar, TrendingDown, Settings, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar } from 'recharts';
import { useToast } from '@/hooks/use-toast';

const CategoryExpenses = () => {
  const { category } = useParams<{ category: string }>();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [selectedYear, setSelectedYear] = useState('2024');
  const [selectedMonth, setSelectedMonth] = useState('12');
  const [chartPeriod, setChartPeriod] = useState<'month' | 'year'>('month');
  
  // Состояние для редактирования категории
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const [editCategoryName, setEditCategoryName] = useState(category || '');
  const [editCategoryLimit, setEditCategoryLimit] = useState('30000');

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

  const years = ['2022', '2023', '2024', '2025'];

  // Примерные данные для графика по месяцам
  const monthlyData = [
    { month: 'Янв', amount: 22000 },
    { month: 'Фев', amount: 25000 },
    { month: 'Мар', amount: 23000 },
    { month: 'Апр', amount: 27000 },
    { month: 'Май', amount: 24000 },
    { month: 'Июн', amount: 26000 },
    { month: 'Июл', amount: 28000 },
    { month: 'Авг', amount: 25000 },
    { month: 'Сен', amount: 24000 },
    { month: 'Окт', amount: 26000 },
    { month: 'Ноя', amount: 27000 },
    { month: 'Дек', amount: 25000 },
  ];

  // Примерные данные для графика по годам
  const yearlyData = [
    { year: '2021', amount: 280000 },
    { year: '2022', amount: 295000 },
    { year: '2023', amount: 310000 },
    { year: '2024', amount: 300000 },
  ];

  // Примерные данные отчета по дням
  const [dailyExpenses, setDailyExpenses] = useState([
    { id: 1, date: '01.12.2024', description: 'Продукты в супермаркете', amount: 1200 },
    { id: 2, date: '03.12.2024', description: 'Кафе', amount: 850 },
    { id: 3, date: '05.12.2024', description: 'Продукты на рынке', amount: 2300 },
    { id: 4, date: '07.12.2024', description: 'Доставка еды', amount: 950 },
    { id: 5, date: '10.12.2024', description: 'Ресторан', amount: 3200 },
    { id: 6, date: '12.12.2024', description: 'Продукты', amount: 1800 },
    { id: 7, date: '15.12.2024', description: 'Кофе', amount: 350 },
    { id: 8, date: '17.12.2024', description: 'Обед', amount: 1250 },
    { id: 9, date: '20.12.2024', description: 'Продукты', amount: 2100 },
    { id: 10, date: '22.12.2024', description: 'Кафе с друзьями', amount: 1650 },
  ]);

  const categoryInfo = {
    'Еда': { color: 'text-blue-500', bgColor: 'bg-blue-500' },
    'Транспорт': { color: 'text-green-500', bgColor: 'bg-green-500' },
    'Развлечения': { color: 'text-purple-500', bgColor: 'bg-purple-500' },
    'Коммунальные': { color: 'text-orange-500', bgColor: 'bg-orange-500' },
    'Здоровье': { color: 'text-red-500', bgColor: 'bg-red-500' },
    'Прочее': { color: 'text-gray-500', bgColor: 'bg-gray-500' },
  };

  const currentCategoryInfo = categoryInfo[category as keyof typeof categoryInfo] || categoryInfo['Прочее'];
  const currentData = chartPeriod === 'month' ? monthlyData : yearlyData;
  const totalMonthExpenses = dailyExpenses.reduce((sum, expense) => sum + expense.amount, 0);

  // Функция для сохранения изменений категории
  const handleSaveCategory = () => {
    // Здесь будет логика сохранения изменений
    toast({
      title: "Категория обновлена",
      description: `Название: ${editCategoryName}, Лимит: ₽${Number(editCategoryLimit).toLocaleString()}`,
    });
    setIsEditDialogOpen(false);
  };

  // Функция для удаления расхода
  const handleDeleteExpense = (id: number) => {
    setDailyExpenses(dailyExpenses.filter(expense => expense.id !== id));
    toast({
      title: "Расход удален",
      description: "Запись о расходе была успешно удалена",
    });
  };

  return (
    <div className="min-h-screen bg-gradient-background">
      <div className="max-w-7xl mx-auto p-6">
        {/* Заголовок */}
        <div className="mb-8">
          <Button
            variant="ghost"
            onClick={() => navigate('/budget')}
            className="mb-4 text-muted-foreground hover:text-foreground hover:bg-muted"
          >
            <ArrowLeft className="w-4 h-4 mr-2" />
            Назад к бюджету
          </Button>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className={`w-6 h-6 rounded-full ${currentCategoryInfo.bgColor}`} />
              <h1 className="text-3xl font-bold text-foreground">
                Категория: {category}
              </h1>
            </div>
            
            <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
              <DialogTrigger asChild>
                <Button variant="outline" size="sm" className="gap-2">
                  <Settings className="w-4 h-4" />
                  Редактировать
                </Button>
              </DialogTrigger>
              <DialogContent className="sm:max-w-[425px]">
                <DialogHeader>
                  <DialogTitle>Редактировать категорию</DialogTitle>
                </DialogHeader>
                <div className="grid gap-4 py-4">
                  <div className="grid grid-cols-4 items-center gap-4">
                    <Label htmlFor="category-name" className="text-right">
                      Название
                    </Label>
                    <Input
                      id="category-name"
                      value={editCategoryName}
                      onChange={(e) => setEditCategoryName(e.target.value)}
                      className="col-span-3"
                      placeholder="Название категории"
                    />
                  </div>
                  <div className="grid grid-cols-4 items-center gap-4">
                    <Label htmlFor="category-limit" className="text-right">
                      Лимит
                    </Label>
                    <Input
                      id="category-limit"
                      type="number"
                      value={editCategoryLimit}
                      onChange={(e) => setEditCategoryLimit(e.target.value)}
                      className="col-span-3"
                      placeholder="Лимит расходов"
                    />
                  </div>
                </div>
                <div className="flex justify-end gap-2">
                  <Button variant="outline" onClick={() => setIsEditDialogOpen(false)}>
                    Отмена
                  </Button>
                  <Button onClick={handleSaveCategory}>
                    Сохранить
                  </Button>
                </div>
              </DialogContent>
            </Dialog>
          </div>
          <p className="text-muted-foreground mt-2">
            Детальная аналитика расходов по категории
          </p>
        </div>

        {/* График колебаний */}
        <Card className="mb-8 shadow-card border-0">
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                <TrendingDown className="w-5 h-5" />
                График расходов
              </CardTitle>
              <div className="flex gap-2">
                <Button
                  variant={chartPeriod === 'month' ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => setChartPeriod('month')}
                  className={chartPeriod === 'month' ? 'bg-gradient-primary hover:opacity-90' : ''}
                >
                  По месяцам
                </Button>
                <Button
                  variant={chartPeriod === 'year' ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => setChartPeriod('year')}
                  className={chartPeriod === 'year' ? 'bg-gradient-primary hover:opacity-90' : ''}
                >
                  По годам
                </Button>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={currentData}>
                <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--muted))" />
                <XAxis 
                  dataKey={chartPeriod === 'month' ? 'month' : 'year'}
                  stroke="hsl(var(--muted-foreground))"
                />
                <YAxis stroke="hsl(var(--muted-foreground))" />
                <Tooltip
                  contentStyle={{
                    backgroundColor: 'hsl(var(--card))',
                    border: '1px solid hsl(var(--border))',
                    borderRadius: '8px',
                    color: 'hsl(var(--foreground))'
                  }}
                  formatter={(value) => [`₽${Number(value).toLocaleString()}`, 'Сумма']}
                />
                <Line
                  type="monotone"
                  dataKey="amount"
                  stroke="hsl(var(--primary))"
                  strokeWidth={3}
                  dot={{ fill: 'hsl(var(--primary))', strokeWidth: 2, r: 6 }}
                  activeDot={{ r: 8, stroke: 'hsl(var(--primary))' }}
                />
              </LineChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* Отчет по дням */}
        <Card className="shadow-card border-0">
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                <Calendar className="w-5 h-5" />
                Отчет по дням
                <span className="text-sm font-normal text-muted-foreground ml-2">
                  (Общая сумма: ₽{totalMonthExpenses.toLocaleString()})
                </span>
              </CardTitle>
              <div className="flex gap-3">
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
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              {dailyExpenses.map((expense, index) => (
                <div 
                  key={expense.id} 
                  className="relative flex items-center justify-between p-4 rounded-lg border border-transparent hover:border-border/20 transition-all duration-300 hover:shadow-card group overflow-hidden"
                >
                  <div className="absolute inset-0 bg-gradient-primary opacity-5" />
                  <div className="relative z-10 flex items-center gap-4">
                    <div className="flex flex-col">
                      <div className="font-medium text-foreground">
                        {expense.description}
                      </div>
                      <div className="text-sm text-muted-foreground">
                        {expense.date}
                      </div>
                    </div>
                  </div>
                  <div className="relative z-10 flex items-center gap-3">
                    <div className="font-semibold text-lg text-foreground">
                      ₽{expense.amount.toLocaleString()}
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="opacity-0 group-hover:opacity-100 transition-opacity duration-200 h-8 w-8 text-destructive hover:text-destructive hover:bg-destructive/10"
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
        </Card>
      </div>
    </div>
  );
};

export default CategoryExpenses;