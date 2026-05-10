import {type FormEvent, useState} from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { KeyRound, Mail, ArrowLeft } from 'lucide-react';

// TODO: implement forgot-password when backend endpoint /auth/api/forgot-password is ready.
// Replace the disabled state below with apiPost('/auth/api/forgot-password', { email }, { requiresAuth: false }).

const ForgotPassword = () => {
  const [email, setEmail] = useState('');
  const [lastAttempt, setLastAttempt] = useState(0);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();

    // Debounce: block repeated submits within 1 second
    if (Date.now() - lastAttempt < 1000) return;
    setLastAttempt(Date.now());

    alert('Функция в разработке');
  };

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
                  maxLength={254}
                  disabled
                  required
                />
              </div>
            </div>

            {/* Disabled until backend endpoint is implemented */}
            <Button
              type="submit"
              className="w-full btn-cta"
              disabled
            >
              Функция в разработке
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
