import { z } from 'zod';

/** Matches @Size(max = 255) on the email of both LoginRequest and RegisterRequest in the auth module. */
export const EMAIL_MAX_LENGTH = 255;
export const PASSWORD_MAX_LENGTH = 128;
export const NAME_MAX_LENGTH = 50;

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Введите почту').max(EMAIL_MAX_LENGTH).email('Введите корректную почту'),
  password: z.string().min(1, 'Введите пароль').max(PASSWORD_MAX_LENGTH),
});

export type LoginFormValues = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    firstName: z.string().trim().min(1, 'Введите имя').max(NAME_MAX_LENGTH),
    lastName: z.string().trim().min(1, 'Введите фамилию').max(NAME_MAX_LENGTH),
    email: z.string().trim().min(1, 'Введите почту').max(EMAIL_MAX_LENGTH).email('Введите корректную почту'),
    password: z.string().min(6, 'Пароль слишком короткий').max(PASSWORD_MAX_LENGTH),
    confirmPassword: z.string().min(1, 'Повторите пароль').max(PASSWORD_MAX_LENGTH),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: 'Пароли не совпадают',
    path: ['confirmPassword'],
  });

export type RegisterFormValues = z.infer<typeof registerSchema>;
