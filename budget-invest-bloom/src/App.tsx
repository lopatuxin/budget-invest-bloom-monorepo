import { lazy, Suspense } from "react";
import { Toaster } from "@/components/ui/toaster";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import * as Sentry from "@sentry/react";
import { Loader2 } from "lucide-react";
import { AuthProvider, useAuth } from "@/contexts/AuthContext";
import HealthGate from "@/components/HealthGate";
import AppRail from "@/components/AppRail";
import BottomNav from "@/components/BottomNav";
import { AppErrorFallback } from "@/components/AppErrorFallback";
import { OperationDialogProvider } from "@/components/operation/OperationDialogProvider";
import { LoginPage } from "@/pages/auth/LoginPage";
import { RegisterPage } from "@/pages/auth/RegisterPage";
import NotFound from "./pages/NotFound";
import { cn } from "@/lib/utils";

const OverviewPage = lazy(() =>
  import("./pages/overview/OverviewPage").then((module) => ({ default: module.OverviewPage }))
);
const Budget = lazy(() => import("./pages/Budget"));
const CategoryPage = lazy(() =>
  import("./pages/category/CategoryPage").then((module) => ({ default: module.CategoryPage }))
);
const Analytics = lazy(() => import("./pages/Analytics"));
const Investments = lazy(() => import("./pages/Investments"));
const ForecastPage = lazy(() => import("./pages/forecast/ForecastPage").then((module) => ({ default: module.ForecastPage })));
const SecurityPage = lazy(() => import("./pages/security/SecurityPage").then((module) => ({ default: module.SecurityPage })));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, gcTime: 5 * 60_000, retry: 1, refetchOnWindowFocus: false },
  },
});

// Full-screen paper loading state shared by the pre-session gate and the lazy page fallback.
const AppLoadingScreen = ({ minHeightClassName = "min-h-screen" }: { minHeightClassName?: string }) => (
  <div className={cn(minHeightClassName, "flex flex-col items-center justify-center gap-3 bg-app-bg")}>
    <Loader2 aria-hidden="true" className="w-6 h-6 text-app-accent animate-spin" />
    <span className="text-[13px] text-app-text-muted">Загрузка…</span>
  </div>
);

const PageLoader = () => <AppLoadingScreen minHeightClassName="min-h-[200px]" />;

// Protected routes list — single source of truth
const protectedRoutes = [
  { path: "/", element: <OverviewPage /> },
  { path: "/budget", element: <Budget /> },
  { path: "/budget/category/:category", element: <CategoryPage /> },
  { path: "/analytics", element: <Analytics /> },
  { path: "/analytics/:tab", element: <Analytics /> },
  { path: "/budget/metric/*", element: <Navigate to="/analytics" replace /> },
  { path: "/investments", element: <Investments /> },
  { path: "/investments/forecast", element: <ForecastPage /> },
  { path: "/investments/analytics", element: <Navigate to="/investments/forecast" replace /> },
  { path: "/investments/security/:ticker", element: <SecurityPage /> },
];

const AppLayout = () => {
  const { isAuthenticated, isInitialized } = useAuth();

  if (!isInitialized) {
    return <AppLoadingScreen />;
  }

  if (!isAuthenticated) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        {/* Redirect all protected routes and unknown paths to login */}
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return (
    <div className="h-screen overflow-hidden">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:px-4 focus:py-2 focus:bg-app-accent focus:text-app-accent-ink focus:rounded-xl"
      >
        Перейти к контенту
      </a>
      <OperationDialogProvider>
        <AppRail />
        <BottomNav />
        <main id="main-content" tabIndex={-1} className="lg:ml-[76px] h-screen overflow-y-auto app-scroll pt-4 px-4 pb-24 lg:pt-7 lg:px-8 lg:pb-8">
          <Suspense fallback={<PageLoader />}>
            <Routes>
              {protectedRoutes.map(({ path, element }) => (
                <Route key={path} path={path} element={element} />
              ))}
              <Route path="/login" element={<Navigate to="/" replace />} />
              <Route path="/register" element={<Navigate to="/" replace />} />
              <Route path="*" element={<NotFound />} />
            </Routes>
          </Suspense>
        </main>
      </OperationDialogProvider>
    </div>
  );
};

const App = () => (
  <Sentry.ErrorBoundary fallback={AppErrorFallback}>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <HealthGate>
            <Toaster />
            <AppLayout />
          </HealthGate>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </Sentry.ErrorBoundary>
);

export default App;
