import { CircleAlert } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { AuthBannerVariant } from '@/pages/auth/authErrors';

interface AuthErrorBannerProps {
  variant: AuthBannerVariant;
  message: string;
}

const VARIANT_CLASSES: Record<AuthBannerVariant, string> = {
  bad: 'bg-app-bad-soft text-app-bad',
  warn: 'bg-app-warn-soft text-app-warn',
};

export function AuthErrorBanner({ variant, message }: AuthErrorBannerProps) {
  return (
    <div role="alert" className={cn('flex items-start gap-2 rounded-lg px-3 py-2.5 text-[13px]', VARIANT_CLASSES[variant])}>
      <CircleAlert aria-hidden="true" className="w-4 h-4 mt-0.5 flex-shrink-0" />
      <span>{message}</span>
    </div>
  );
}
