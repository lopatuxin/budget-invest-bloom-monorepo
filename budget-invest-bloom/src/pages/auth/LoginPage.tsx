import { useState, type MouseEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { AuthLayout } from '@/pages/auth/AuthLayout';
import { AuthErrorBanner } from '@/pages/auth/AuthErrorBanner';
import { PasswordInput } from '@/pages/auth/PasswordInput';
import { EMAIL_MAX_LENGTH, PASSWORD_MAX_LENGTH, loginSchema, type LoginFormValues } from '@/pages/auth/authSchemas';
import { loginErrorBanner, type AuthBannerContent } from '@/pages/auth/authErrors';
import { useAuth } from '@/contexts/AuthContext';
import { apiPost } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';

interface LoginResponseBody {
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
}

const FIELD_HEIGHT = 'h-[46px] lg:h-[42px] aria-[invalid=true]:border-app-bad';

const MALFORMED_RESPONSE_BANNER: AuthBannerContent = { variant: 'bad', message: 'Не удалось войти, попробуйте ещё раз' };

function isLoginResponseBody(body: Partial<LoginResponseBody> | undefined): body is LoginResponseBody {
  return typeof body?.accessToken === 'string' && typeof body.user?.userId === 'string';
}

export function LoginPage() {
  const [banner, setBanner] = useState<AuthBannerContent | null>(null);
  const { setAuthData } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const prefillEmail = (location.state as { email?: string } | null)?.email ?? '';

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: prefillEmail, password: '' },
  });

  const handleSubmit = async (values: LoginFormValues) => {
    setBanner(null);
    let body: Partial<LoginResponseBody> | undefined;
    try {
      const data = await apiPost<{ body?: Partial<LoginResponseBody> }>(
        '/auth/api/login',
        { email: values.email, password: values.password },
        { requiresAuth: false }
      );
      body = data?.body;
    } catch (error) {
      setBanner(loginErrorBanner(error));
      return;
    }
    if (!isLoginResponseBody(body)) {
      setBanner(MALFORMED_RESPONSE_BANNER);
      return;
    }
    setAuthData(body.accessToken, body.user);
    navigate('/');
  };

  const goToRegister = (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    navigate('/register', { state: { email: form.getValues('email') } });
  };

  const isSubmitting = form.formState.isSubmitting;

  return (
    <AuthLayout title="Войти" subtitle="Регистрация — по ссылке внизу">
      <Form {...form}>
        <form onSubmit={form.handleSubmit(handleSubmit)} className="flex flex-col gap-4" noValidate>
          {banner && <AuthErrorBanner variant={banner.variant} message={banner.message} />}

          <FormField
            control={form.control}
            name="email"
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-[13px] font-normal text-app-text-muted">Почта</FormLabel>
                <FormControl>
                  <Input type="email" autoComplete="username" maxLength={EMAIL_MAX_LENGTH} disabled={isSubmitting} className={FIELD_HEIGHT} {...field} />
                </FormControl>
                <FormMessage className="text-[12px]" />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="password"
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-[13px] font-normal text-app-text-muted">Пароль</FormLabel>
                <FormControl>
                  <PasswordInput autoComplete="current-password" maxLength={PASSWORD_MAX_LENGTH} disabled={isSubmitting} className={FIELD_HEIGHT} {...field} />
                </FormControl>
                <FormMessage className="text-[12px]" />
              </FormItem>
            )}
          />

          <Button type="submit" disabled={isSubmitting} className="w-full h-[46px] lg:h-[42px]">
            {isSubmitting && <Loader2 aria-hidden="true" className="w-4 h-4 animate-spin" />}
            {isSubmitting ? 'Входим…' : 'Войти'}
          </Button>

          <p className="text-center text-[13px] text-app-text-muted">
            Нет аккаунта?{' '}
            <Link to="/register" onClick={goToRegister} className="text-app-accent font-medium hover:underline">
              Создать
            </Link>
          </p>
        </form>
      </Form>
    </AuthLayout>
  );
}
