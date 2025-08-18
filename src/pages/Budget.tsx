import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Plus, Minus, DollarSign, CreditCard } from 'lucide-react';
import FinanceCard from '@/components/FinanceCard';
import { useState } from 'react';
import { useToast } from '@/hooks/use-toast';
import { useNavigate } from 'react-router-dom';

const Budget = () => {
  const { toast } = useToast();
  const navigate = useNavigate();
  const [selectedMonth, setSelectedMonth] = useState('12');
  const [selectedYear, setSelectedYear] = useState('2024');
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [selectedOperationType, setSelectedOperationType] = useState<'expense' | 'income' | null>(null);
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

  // Примерные данные
  const income = 150000;
  const expenses = 89500;
  const balance = income - expenses;

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

  const categories = [
    { name: 'Еда', amount: 25000, budget: 30000, color: 'bg-blue-500' },
    { name: 'Транспорт', amount: 15000, budget: 20000, color: 'bg-green-500' },
    { name: 'Развлечения', amount: 12000, budget: 15000, color: 'bg-purple-500' },
    { name: 'Коммунальные', amount: 18000, budget: 18000, color: 'bg-orange-500' },
    { name: 'Здоровье', amount: 8500, budget: 10000, color: 'bg-red-500' },
    { name: 'Прочее', amount: 11000, budget: 15000, color: 'bg-gray-500' },
  ];

  const getProgressPercentage = (amount: number, budget: number) => {
    return Math.min((amount / budget) * 100, 100);
  };

  const incomeSources = ['Зарплата', 'Фриланс', 'Инвестиции', 'Подарки', 'Прочее'];

  const handleAddExpense = () => {
    if (!expenseForm.amount || !expenseForm.category) {
      toast({
        title: "Ошибка",
        description: "Заполните обязательные поля",
        variant: "destructive"
      });
      return;
    }

    toast({
      title: "Расход добавлен",
      description: `Добавлен расход ${expenseForm.amount}₽ в категорию "${expenseForm.category}"`,
    });

    setExpenseForm({ amount: '', category: '', description: '' });
    resetDialog();
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

  const resetDialog = () => {
    setSelectedOperationType(null);
    setIsDialogOpen(false);
  };

  return (
    <div className="min-h-screen bg-gradient-background">
      <div className="max-w-7xl mx-auto p-6">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-foreground mb-2">Бюджет</h1>
          <p className="text-muted-foreground">Управляйте своими доходами и расходами</p>
        </div>

        {/* Карточки обзора */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <FinanceCard
            title="Доходы"
            value={`₽${income.toLocaleString()}`}
            icon={<Plus className="w-5 h-5" />}
            trend={{ value: "+8.2%", isPositive: true }}
            gradient="success"
          />
          <FinanceCard
            title="Расходы"
            value={`₽${expenses.toLocaleString()}`}
            icon={<Minus className="w-5 h-5" />}
            trend={{ value: "+3.1%", isPositive: false }}
            gradient="secondary"
          />
          <FinanceCard
            title="Остаток"
            value={`₽${balance.toLocaleString()}`}
            icon={<DollarSign className="w-5 h-5" />}
            trend={{ value: "+12.8%", isPositive: true }}
            gradient="primary"
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
                      <div className="flex gap-2 justify-center">
                        <Button 
                          variant={selectedOperationType === 'expense' ? 'default' : 'outline'}
                          size="sm"
                          onClick={() => setSelectedOperationType('expense')}
                          className={selectedOperationType === 'expense' ? 'bg-gradient-primary hover:opacity-90' : ''}
                        >
                          <Minus className="w-4 h-4 mr-2" />
                          Добавить расход
                        </Button>
                        <Button 
                          variant={selectedOperationType === 'income' ? 'default' : 'outline'}
                          size="sm"
                          onClick={() => setSelectedOperationType('income')}
                          className={selectedOperationType === 'income' ? 'bg-gradient-success hover:opacity-90' : ''}
                        >
                          <Plus className="w-4 h-4 mr-2" />
                          Добавить доход
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
                                  <SelectItem key={category.name} value={category.name}>
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
                            >
                              Добавить
                            </Button>
                          </div>
                        </>
                      ) : (
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
            <CardContent className="space-y-4">
              {categories.map((category, index) => {
                const percentage = getProgressPercentage(category.amount, category.budget);
                const isOverBudget = category.amount > category.budget;
                
                return (
                  <div 
                    key={index} 
                    className="relative space-y-2 p-4 rounded-lg transition-all duration-300 hover:shadow-card hover:scale-105 cursor-pointer border border-transparent hover:border-border/20 overflow-hidden"
                    onClick={() => navigate(`/budget/category/${encodeURIComponent(category.name)}`)}
                  >
                    <div className="absolute inset-0 bg-gradient-primary opacity-5" />
                    <div className="relative z-10 flex justify-between items-center">
                      <div className="flex items-center space-x-3">
                        <div className={`w-3 h-3 rounded-full ${category.color}`} />
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