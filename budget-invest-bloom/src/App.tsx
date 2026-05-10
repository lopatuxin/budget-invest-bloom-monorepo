import { lazy, Suspense } from "react";
import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route, Navigate, useLocation } from "react-router-dom";
import * as Sentry from "@sentry/react";
import { AuthProvider, useAuth } from "@/contexts/AuthContext";
import HealthGate from "@/components/HealthGate";
import Navigation from "@/components/Navigation";
import Sidebar from "@/components/Sidebar";
import BottomNav from "@/components/BottomNav";
import Register from "./pages/Register";
import Login from "./pages/Login";
import ForgotPassword from "./pages/ForgotPassword";
import NotFound from "./pages/NotFound";

const Index = lazy(() => import("./pages/Index"));
const Budget = lazy(() => import("./pages/Budget"));
const CategoryExpenses = lazy(() => import("./pages/CategoryExpenses"));
const MetricDetails = lazy(() => import("./pages/MetricDetails"));
const Investments = lazy(() => import("./pages/Investments"));
const PortfolioAnalytics = lazy(() => import("./pages/PortfolioAnalytics"));
const SecurityDetails = lazy(() => import("./pages/SecurityDetails"));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, gcTime: 5 * 60_000, retry: 1, refetchOnWindowFocus: false },
  },
});

// Lightweight loader shown while lazy page chunks are being fetched
const PageLoader = () => (
  <div className="dashboard-bg flex items-center justify-center min-h-[200px]">
    <div>Загрузка...</div>
  </div>
);

// Fallback component for Error Boundary
const ErrorFallback = ({ resetError }: { resetError: () => void }) => (
  <div className="dashboard-bg flex items-center justify-center">
    <div className="max-w-md p-8 glass-card text-center">
      <h1 className="text-2xl font-bold text-red-400 mb-4">Что-то пошло не так</h1>
      <p className="text-dashboard-text-muted mb-6">
        Произошла непредвиденная ошибка. Мы уже получили уведомление и работаем над исправлением.
      </p>
      <button
        onClick={resetError}
        className="px-4 py-2 bg-emerald-500 text-white rounded-xl hover:bg-emerald-600 transition-colors"
      >
        Попробовать снова
      </button>
    </div>
  </div>
);

// Protected routes list — single source of truth
const protectedRoutes = [
  { path: "/", element: <Index /> },
  { path: "/budget", element: <Budget /> },
  { path: "/budget/category/:category", element: <CategoryExpenses /> },
  { path: "/budget/metric/:metric", element: <MetricDetails /> },
  { path: "/investments", element: <Investments /> },
  { path: "/investments/analytics", element: <PortfolioAnalytics /> },
  { path: "/investments/security/:ticker", element: <SecurityDetails /> },
];

const AppLayout = () => {
  const { isAuthenticated, isInitialized } = useAuth();
  const location = useLocation();
  const isAuthPage = ['/login', '/register', '/forgot-password'].includes(location.pathname);

  if (!isInitialized) {
    return (
      <div className="dashboard-bg flex items-center justify-center min-h-screen">
        <div>Загрузка...</div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="dashboard-bg">
        {!isAuthPage && <Navigation />}
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          {/* Redirect all protected routes and unknown paths to login */}
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </div>
    );
  }

  return (
    <div className="dashboard-bg h-screen overflow-hidden">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:px-4 focus:py-2 focus:bg-emerald-500 focus:text-white focus:rounded-xl"
      >
        Перейти к контенту
      </a>
      <Sidebar />
      <BottomNav />
      <main id="main-content" tabIndex={-1} className="lg:ml-[264px] h-screen overflow-y-auto dashboard-scroll p-4 lg:p-6 pb-20 lg:pb-6">
        <Suspense fallback={<PageLoader />}>
          <Routes>
            {protectedRoutes.map(({ path, element }) => (
              <Route key={path} path={path} element={element} />
            ))}
            <Route path="/login" element={<Navigate to="/" replace />} />
            <Route path="/register" element={<Navigate to="/" replace />} />
            <Route path="/forgot-password" element={<Navigate to="/" replace />} />
            {/* ADD ALL CUSTOM ROUTES ABOVE THE CATCH-ALL "*" ROUTE */}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Suspense>
      </main>
    </div>
  );
};

const App = () => (
  <Sentry.ErrorBoundary fallback={ErrorFallback}>
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <BrowserRouter>
          <AuthProvider>
            <HealthGate>
              <Toaster />
              <Sonner />
              <AppLayout />
            </HealthGate>
          </AuthProvider>
        </BrowserRouter>
      </TooltipProvider>
    </QueryClientProvider>
  </Sentry.ErrorBoundary>
);

export default App;
