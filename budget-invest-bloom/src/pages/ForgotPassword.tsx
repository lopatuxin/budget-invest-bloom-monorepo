import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useToast } from '@/hooks/use-toast';
import { KeyRound, Mail, ArrowLeft, CheckCircle } from 'lucide-react';

const ForgotPassword = () => {
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isEmailSent, setIsEmailSent] = useState(false);
  const { toast } = useToast();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    // Простая валидация
    if (!email) {
      toast({
        title: "Ошибка",
        description: "Пожалуйста, введите email адрес",
        variant: "destructive",
      });
      setIsLoading(false);
      return;
    }

    // Валидация формата email
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      toast({
        title: "Ошибка",
        description: "Введите корректный email адрес",
        variant: "destructive",
      });
      setIsLoading(false);
      return;
    }

    // Здесь будет вызов вашего API
    try {
      // Имитация запроса
      await new Promise(resolve => setTimeout(resolve, 1500));

      setIsEmailSent(true);
      toast({
        title: "Письмо отправлено!",
        description: "Проверьте свою почту для восстановления пароля",
      });
    } catch (error) {
      toast({
        title: "Ошибка",
        description: "Не удалось отправить письмо. Попробуйте снова.",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  const handleResendEmail = async () => {
    setIsLoading(true);
    try {
      await new Promise(resolve => setTimeout(resolve, 1000));
      toast({
        title: "Письмо отправлено повторно",
        description: "Проверьте свою почту",
      });
    } catch (error) {
      toast({
        title: "Ошибка",
        description: "Не удалось отправить письмо повторно",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  if (isEmailSent) {
    return (
      <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center p-4">
        <div className="glass-card w-full max-w-md p-0">
          <CardHeader className="text-center">
            <div className="w-12 h-12 bg-emerald-500/20 rounded-lg flex items-center justify-center mx-auto mb-4">
              <CheckCircle className="w-6 h-6 text-emerald-400" />
            </div>
            <CardTitle className="text-2xl font-bold text-dashboard-text">Письмо отправлено</CardTitle>
            <CardDescription className="text-dashboard-text-muted">
              Мы отправили инструкции по восстановлению пароля на {email}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="text-center space-y-4">
              <p className="text-sm text-dashboard-text-muted">
                Не получили письмо? Проверьте папку "Спам" или попробуйте снова.
              </p>

              <button
                onClick={handleResendEmail}
                className="w-full bg-white/[0.06] border border-white/[0.12] rounded-xl text-dashboard-text hover:bg-white/10 py-2 px-4 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                disabled={isLoading}
              >
                {isLoading ? "Отправка..." : "Отправить повторно"}
              </button>
            </div>

            <div className="text-center pt-4">
              <Link
                to="/login"
                className="inline-flex items-center text-sm text-emerald-400 hover:underline"
              >
                <ArrowLeft className="w-4 h-4 mr-1" />
                Вернуться к входу
              </Link>
            </div>
          </CardContent>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center p-4">
      <div className="glass-card w-full max-w-md p-0">
        <CardHeader className="text-center">
          <div className="w-12 h-12 bg-emerald-500/20 rounded-lg flex items-center justify-center mx-auto mb-4">
            <KeyRound className="w-6 h-6 text-emerald-400" />
          </div>
          <CardTitle className="text-2xl font-bold text-dashboard-text">Забыли пароль?</CardTitle>
          <CardDescription className="text-dashboard-text-muted">
            Введите ваш email и мы отправим инструкции по восстановлению
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email" className="text-dashboard-text">Email адрес</Label>
              <div className="relative">
                <Mail className="absolute left-3 top-3 h-4 w-4 text-dashboard-text-muted" />
                <Input
                  id="email"
                  type="email"
                  placeholder="your@email.com"
                  className="pl-10 bg-white/5 border-white/10 text-dashboard-text placeholder:text-dashboard-text-muted"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>
            </div>

            <Button
              type="submit"
              className="w-full bg-emerald-500 hover:bg-emerald-600 text-white"
              disabled={isLoading}
            >
              {isLoading ? "Отправка..." : "Отправить инструкции"}
            </Button>
          </form>

          <div className="mt-6 text-center">
            <p className="text-sm text-dashboard-text-muted mb-2">
              Вспомнили пароль?
            </p>
            <Link
              to="/login"
              className="inline-flex items-center text-sm text-emerald-400 hover:underline"
            >
              <ArrowLeft className="w-4 h-4 mr-1" />
              Вернуться к входу
            </Link>
          </div>
        </CardContent>
      </div>
    </div>
  );
};

export default ForgotPassword;
