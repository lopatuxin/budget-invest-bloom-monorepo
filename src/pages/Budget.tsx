import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Plus, Minus, DollarSign, CreditCard } from 'lucide-react';
import FinanceCard from '@/components/FinanceCard';
import { useState } from 'react';

const Budget = () => {
  const [selectedMonth, setSelectedMonth] = useState('12');
  const [selectedYear, setSelectedYear] = useState('2024');

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
                <Button size="sm" className="bg-gradient-primary hover:opacity-90">
                  <Plus className="w-4 h-4 mr-2" />
                  Добавить
                </Button>
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
                  <div key={index} className="space-y-2">
                    <div className="flex justify-between items-center">
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
                    <div className="w-full bg-muted rounded-full h-2">
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

          {/* Быстрые действия */}
          <Card className="shadow-card border-0">
            <CardHeader>
              <CardTitle>Быстрые действия</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <Button 
                className="w-full bg-gradient-success hover:opacity-90 justify-start h-12"
                size="lg"
              >
                <Plus className="w-5 h-5 mr-3" />
                Добавить доход
              </Button>
              <Button 
                variant="outline" 
                className="w-full justify-start h-12 border-destructive text-destructive hover:bg-destructive hover:text-destructive-foreground"
                size="lg"
              >
                <Minus className="w-5 h-5 mr-3" />
                Добавить расход
              </Button>
              <Button 
                variant="outline" 
                className="w-full justify-start h-12"
                size="lg"
              >
                <CreditCard className="w-5 h-5 mr-3" />
                Настроить бюджет
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default Budget;