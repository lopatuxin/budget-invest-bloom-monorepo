import {FormEvent, useState} from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/contexts/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useToast } from '@/hooks/use-toast';
import { LogIn, Eye, EyeOff, Mail, Lock } from 'lucide-react';
import { apiPost, ApiError } from '@/lib/api';

interface LoginResponse {
  body: {
    accessToken: string;
    user: {
      userId: string;
      email: string;
      firstName?: string;
      lastName?: string;
      name?: string;
      isActive: boolean;
      isVerified: boolean;
      roles: string[];
      lastLoginAt: string;
    };
  };
}

const Login = () => {
  const [showPassword, setShowPassword] = useState(false);
  const [formData, setFormData] = useState({
    email: '',
    password: '',
  });
  const [isLoading, setIsLoading] = useState(false);
  const [lastAttempt, setLastAttempt] = useState(0);
  const { toast } = useToast();
  const { setAuthData } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    // Debounce: block repeated submits within 1 second
    if (Date.now() - lastAttempt < 1000) return;

    setIsLoading(true);

    // Basic validation
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
      const data = await apiPost<LoginResponse>('/auth/api/login', {
        email: formData.email,
        password: formData.password,
      }, { requiresAuth: false });

      // Validate response structure before using it
      if (
        typeof data?.body?.accessToken !== 'string' ||
        !data.body.accessToken ||
        typeof data?.body?.user !== 'object' ||
        !data.body.user?.userId ||
        !data.body.user?.email
      ) {
        toast({
          title: "Ошибка",
          description: "Не удалось войти, попробуйте позже",
          variant: "destructive",
        });
        return;
      }

      // Save token and user data via AuthContext
      // refreshToken arrives in HttpOnly Cookie automatically
      setAuthData(data.body.accessToken, data.body.user);

      toast({
        title: "Успешный вход!",
        description: "Добро пожаловать в FinanceApp",
      });

      navigate('/');
    } catch (error) {
      setLastAttempt(Date.now());

      // apiPost throws ApiError which carries the server error code and HTTP status
      const errorObj = error instanceof ApiError ? error : (error as Error);
      const errorCode = error instanceof ApiError ? error.code : undefined;
      const errorStatus = error instanceof ApiError ? error.status : undefined;

      switch (errorCode) {
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
            description: "Аккаунт временно заблокирован из-за множественных неудачных попыток входа",
            variant: "destructive",
          });
          break;

        case 'ACCOUNT_INACTIVE':
          toast({
            title: "Аккаунт неактивен",
            description: "Аккаунт деактивирован. Обратитесь в службу поддержки",
            variant: "destructive",
          });
          break;

        case 'RATE_LIMIT_EXCEEDED':
          toast({
            title: "Слишком много попыток",
            description: "Слишком много попыток входа. Попробуйте через несколько минут",
            variant: "destructive",
          });
          break;

        case 'MISSING_REQUIRED_FIELDS':
          toast({
            title: "Ошибка",
            description: "Отсутствуют обязательные поля",
            variant: "destructive",
          });
          break;

        default:
          toast({
            title: "Ошибка",
            description: errorObj.message || "Не удалось войти в систему",
            variant: "destructive",
          });
      }

      console.warn('login failed', errorCode || errorStatus || 'unknown');
    } finally {
      setIsLoading(false);
    }
  };

  const handleInputChange = (field: string, value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }));
  };

  return (
    <div className="dashboard-bg min-h-screen flex items-center justify-center p-4">
      <div className="glass-card w-full max-w-md p-0 animate-fade-slide-up" style={{ borderLeft: '3px solid #10B981' }}>
        <CardHeader className="text-center pt-8">
          <div className="w-12 h-12 bg-emerald-500/10 rounded-xl flex items-center justify-center mx-auto mb-4 border border-emerald-500/20" style={{ boxShadow: '0 0 20px rgba(16,185,129,0.25)' }}>
            <LogIn className="w-6 h-6 text-emerald-400" />
          </div>
          <CardTitle className="text-2xl font-bold text-emerald-400">Добро пожаловать</CardTitle>
          <CardDescription className="text-dashboard-text-muted">
            Войдите в свой аккаунт для продолжения
          </CardDescription>
        </CardHeader>
        <CardContent className="pb-8">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email" className="text-dashboard-text-muted">Email</Label>
              <div className="relative">
                <Mail className="absolute left-3 top-3 h-4 w-4 text-dashboard-text-muted" />
                <Input
                  id="email"
                  type="email"
                  placeholder="your@email.com"
                  className="pl-10 text-dashboard-text placeholder:text-dashboard-text-muted focus:border-emerald-500/50 transition-colors"
                  value={formData.email}
                  onChange={(e) => handleInputChange('email', e.target.value)}
                  maxLength={254}
                  required
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="password" className="text-dashboard-text-muted">Пароль</Label>
              <div className="relative">
                <Lock className="absolute left-3 top-3 h-4 w-4 text-dashboard-text-muted" />
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Введите пароль"
                  className="pl-10 pr-10 text-dashboard-text placeholder:text-dashboard-text-muted focus:border-emerald-500/50 transition-colors"
                  value={formData.password}
                  onChange={(e) => handleInputChange('password', e.target.value)}
                  maxLength={128}
                  required
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent text-dashboard-text-muted hover:text-dashboard-text"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4" />
                  ) : (
                    <Eye className="h-4 w-4" />
                  )}
                </Button>
              </div>
            </div>

            <Button
              type="submit"
              className="w-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/30 rounded-xl transition-colors h-10"
              disabled={isLoading}
            >
              {isLoading ? "Вход..." : "Войти"}
            </Button>
          </form>

          <div className="mt-4 text-center">
            <Link
              to="/forgot-password"
              className="text-sm text-emerald-400 hover:text-emerald-300 transition-colors hover:underline"
            >
              Забыли пароль?
            </Link>
          </div>

          <div className="mt-6 text-center border-t border-white/10 pt-6">
            <p className="text-sm text-dashboard-text-muted">
              Нет аккаунта?{' '}
              <Link to="/register" className="text-emerald-400 font-medium hover:text-emerald-300 transition-colors hover:underline">
                Зарегистрироваться
              </Link>
            </p>
          </div>
        </CardContent>
      </div>
    </div>
  );
};

export default Login;
