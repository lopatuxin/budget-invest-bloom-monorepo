import { type ReactNode } from 'react';
import { AuthBrandPane } from '@/pages/auth/AuthBrandPane';

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  children: ReactNode;
}

// Shared "разворот" (split) layout for /login and /register: a 640px accent promise
// panel next to a 380px-wide form on paper (desktop, >=1024px), or a green band on
// top of the form (mobile) — see docs/plans/auth-and-shell-redesign.md.
export function AuthLayout({ title, subtitle, children }: AuthLayoutProps) {
  return (
    <div className="min-h-screen bg-app-bg lg:grid lg:grid-cols-[640px_minmax(0,1fr)]">
      <AuthBrandPane />
      <div className="flex items-center justify-center px-5 pt-6 pb-6 lg:p-[60px]">
        <div className="w-full lg:w-[380px] flex flex-col gap-4">
          <div className="flex flex-col gap-1 mb-2">
            <h1 className="font-display text-[28px] lg:text-[34px] leading-[1.05] text-app-text">{title}</h1>
            <p className="text-[13px] text-app-text-muted">{subtitle}</p>
          </div>
          {children}
        </div>
      </div>
    </div>
  );
}
