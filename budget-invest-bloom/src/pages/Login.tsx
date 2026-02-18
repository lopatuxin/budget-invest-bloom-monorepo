import {FormEvent, useState} from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/contexts/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { useToast } from '@/hooks/use-toast';
import { LogIn, Mail, Lock, Eye, EyeOff } from 'lucide-react';
import * as React from "react";

const Login = () => {
  const [showPassword, setShowPassword] = useState(false);
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    rememberMe: false
  });
  const [isLoading, setIsLoading] = useState(false);
  const { toast } = useToast();
  const { setAuthData } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    // Простая валидация
    if (!formData.email || !formData.password) {
      toast({
        title: "Ошибка",
        description: "Пожалуйста, заполните все поля",
        variant: "destructive",
      });
      setIsLoading(false);
      return;
    }

    try {
      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/auth/api/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({
          data: {
            email: formData.email,
            password: formData.password,
          }
        }),
      });

      if (!response.ok) {
        const errorData = await response.json();

        // Обработка различных типов ошибок согласно контракту
        switch (errorData.error) {
          case 'INVALID_CREDENTIALS':
            toast({
              title: "Ошибка входа",
              description: "Неверный email или пароль",
              variant: "destructive",
            });
            break;

          case 'ACCOUNT_LOCKED':
            toast({
              title: "Аккаунт заблокирован",
              description: errorData.message || "Аккаунт временно заблокирован из-за множественных неудачных попыток входа",
              variant: "destructive",
            });
            break;

          case 'ACCOUNT_INACTIVE':
            toast({
              title: "Аккаунт неактивен",
              description: errorData.message || "Аккаунт деактивирован. Обратитесь в службу поддержки",
              variant: "destructive",
            });
            break;

          case 'RATE_LIMIT_EXCEEDED':
            toast({
              title: "Слишком много попыток",
              description: errorData.message || "Слишком много попыток входа. Попробуйте через несколько минут",
              variant: "destructive",
            });
            break;

          case 'MISSING_REQUIRED_FIELDS':
            toast({
              title: "Ошибка",
              description: errorData.message || "Отсутствуют обязательные поля",
              variant: "destructive",
            });
            break;

          default:
            toast({
              title: "Ошибка",
              description: errorData.message || "Не удалось войти в систему",
              variant: "destructive",
            });
        }
        return;
      }

      const data = await response.json();

      // Извлекаем данные из body, так как бэк оборачивает ответ
      const { accessToken, user } = data.body;

      // Сохраняем токен и данные пользователя через AuthContext
      // refreshToken приходит в HttpOnly Cookie автоматически
      setAuthData(accessToken, user);

      toast({
        title: "Успешный вход!",
        description: "Добро пожаловать в FinanceApp",
      });

      // Делаем редирект на главную страницу
      navigate('/');
    } catch (error) {
      console.error('Login error:', error);
      toast({
        title: "Ошибка",
        description: "Не удалось подключиться к серверу. Попробуйте снова.",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  const handleInputChange = (field: string, value: string | boolean) => {
    setFormData(prev => ({ ...prev, [field]: value }));
  };

  return (
    <div className="min-h-screen bg-gradient-background flex items-center justify-center p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <div className="w-12 h-12 bg-gradient-primary rounded-lg flex items-center justify-center mx-auto mb-4">
            <LogIn className="w-6 h-6 text-primary-foreground" />
          </div>
          <CardTitle className="text-2xl font-bold">Добро пожаловать</CardTitle>
          <CardDescription>
            Войдите в свой аккаунт для продолжения
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <div className="relative">
                <Mail className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Input
                  id="email"
                  type="email"
                  placeholder="your@email.com"
                  className="pl-10"
                  value={formData.email}
                  onChange={(e) => handleInputChange('email', e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">Пароль</Label>
              <div className="relative">
                <Lock className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Введите пароль"
                  className="pl-10 pr-10"
                  value={formData.password}
                  onChange={(e) => handleInputChange('password', e.target.value)}
                  required
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4 text-muted-foreground" />
                  ) : (
                    <Eye className="h-4 w-4 text-muted-foreground" />
                  )}
                </Button>
              </div>
            </div>

            <div className="flex items-center space-x-2">
              <Checkbox
                id="rememberMe"
                checked={formData.rememberMe}
                onCheckedChange={(checked) => handleInputChange('rememberMe', checked as boolean)}
              />
              <Label htmlFor="rememberMe" className="text-sm font-normal">
                Запомнить меня
              </Label>
            </div>

            <Button
              type="submit"
              className="w-full bg-gradient-primary hover:opacity-90"
              disabled={isLoading}
            >
              {isLoading ? "Вход..." : "Войти"}
            </Button>
          </form>

          <div className="mt-4 text-center">
            <Link 
              to="/forgot-password" 
              className="text-sm text-primary hover:underline"
            >
              Забыли пароль?
            </Link>
          </div>

          <div className="mt-6 text-center">
            <p className="text-sm text-muted-foreground">
              Нет аккаунта?{' '}
              <Link to="/register" className="text-primary font-medium hover:underline">
                Зарегистрироваться
              </Link>
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default Login;