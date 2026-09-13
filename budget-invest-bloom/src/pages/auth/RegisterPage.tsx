import { useState, type MouseEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { AuthLayout } from '@/pages/auth/AuthLayout';
import { AuthErrorBanner } from '@/pages/auth/AuthErrorBanner';
import { PasswordInput } from '@/pages/auth/PasswordInput';
import {
  EMAIL_MAX_LENGTH,
  NAME_MAX_LENGTH,
  PASSWORD_MAX_LENGTH,
  registerSchema,
  type RegisterFormValues,
} from '@/pages/auth/authSchemas';
import { registerErrorBanner, registerFieldErrors, type AuthBannerContent } from '@/pages/auth/authErrors';
import { apiPost } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { useToast } from '@/hooks/use-toast';

const FIELD_HEIGHT = 'h-[46px] lg:h-[42px] aria-[invalid=true]:border-app-bad';

export function RegisterPage() {
  const [banner, setBanner] = useState<AuthBannerContent | null>(null);
  const { toast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const prefillEmail = (location.state as { email?: string } | null)?.email ?? '';

  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { firstName: '', lastName: '', email: prefillEmail, password: '', confirmPassword: '' },
  });

  const handleSubmit = async (values: RegisterFormValues) => {
    setBanner(null);
    try {
      await apiPost(
        '/auth/api/register',
        { firstName: values.firstName, lastName: values.lastName, email: values.email, password: values.password },
        { requiresAuth: false }
      );
      toast({ title: 'Аккаунт создан, теперь войдите' });
      navigate('/login', { state: { email: values.email } });
    } catch (error) {
      const fieldErrors = Object.entries(registerFieldErrors(error)) as [keyof RegisterFormValues, string][];
      if (fieldErrors.length > 0) {
        for (const [name, message] of fieldErrors) {
          form.setError(name, { message });
        }
        return;
      }
      setBanner(registerErrorBanner(error));
    }
  };

  const goToLogin = (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    navigate('/login', { state: { email: form.getValues('email') } });
  };

  const isSubmitting = form.formState.isSubmitting;

  return (
    <AuthLayout title="Новый аккаунт" subtitle="Имя нужно только для подписи в приложении">
      <Form {...form}>
        <form onSubmit={form.handleSubmit(handleSubmit)} className="flex flex-col gap-4" noValidate>
          {banner && <AuthErrorBanner variant={banner.variant} message={banner.message} />}

          <div className="grid grid-cols-2 gap-3">
            <FormField
              control={form.control}
              name="firstName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-[13px] font-normal text-app-text-muted">Имя</FormLabel>
                  <FormControl>
                    <Input maxLength={NAME_MAX_LENGTH} disabled={isSubmitting} className={FIELD_HEIGHT} {...field} />
                  </FormControl>
                  <FormMessage className="text-[12px]" />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="lastName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-[13px] font-normal text-app-text-muted">Фамилия</FormLabel>
                  <FormControl>
                    <Input maxLength={NAME_MAX_LENGTH} disabled={isSubmitting} className={FIELD_HEIGHT} {...field} />
                  </FormControl>
                  <FormMessage className="text-[12px]" />
                </FormItem>
              )}
            />
          </div>

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
                  <PasswordInput autoComplete="new-password" maxLength={PASSWORD_MAX_LENGTH} disabled={isSubmitting} className={FIELD_HEIGHT} {...field} />
                </FormControl>
                <FormDescription className="text-[11px] text-app-text-dim">от 6 символов</FormDescription>
                <FormMessage className="text-[12px]" />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="confirmPassword"
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-[13px] font-normal text-app-text-muted">Пароль ещё раз</FormLabel>
                <FormControl>
                  <PasswordInput autoComplete="new-password" maxLength={PASSWORD_MAX_LENGTH} disabled={isSubmitting} className={FIELD_HEIGHT} {...field} />
                </FormControl>
                <FormMessage className="text-[12px]" />
              </FormItem>
            )}
          />

          <Button type="submit" disabled={isSubmitting} className="w-full h-[46px] lg:h-[42px]">
            {isSubmitting && <Loader2 aria-hidden="true" className="w-4 h-4 animate-spin" />}
            {isSubmitting ? 'Создаём…' : 'Создать аккаунт'}
          </Button>

          <p className="text-center text-[13px] text-app-text-muted">
            Уже есть аккаунт?{' '}
            <Link to="/login" onClick={goToLogin} className="text-app-accent font-medium hover:underline">
              Войти
            </Link>
          </p>
        </form>
      </Form>
    </AuthLayout>
  );
}
