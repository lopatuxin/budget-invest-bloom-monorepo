import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route, useLocation } from "react-router-dom";
import * as Sentry from "@sentry/react";
import { AuthProvider, useAuth } from "@/contexts/AuthContext";
import HealthGate from "@/components/HealthGate";
import Navigation from "@/components/Navigation";
import Sidebar from "@/components/Sidebar";
import BottomNav from "@/components/BottomNav";
import Index from "./pages/Index";
import Budget from "./pages/Budget";
import Investments from "./pages/Investments";
import PortfolioAnalytics from "./pages/PortfolioAnalytics";
import CategoryExpenses from "./pages/CategoryExpenses";
import MetricDetails from "./pages/MetricDetails";
import Register from "./pages/Register";
import Login from "./pages/Login";
import ForgotPassword from "./pages/ForgotPassword";
import NotFound from "./pages/NotFound";
import SecurityDetails from "./pages/SecurityDetails";

const queryClient = new QueryClient();

// Fallback компонент для Error Boundary
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

const AppLayout = () => {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  const isAuthPage = ['/login', '/register', '/forgot-password'].includes(location.pathname);

  if (!isAuthenticated) {
    return (
      <div className="dashboard-bg">
        {!isAuthPage && <Navigation />}
        <Routes>
          <Route path="/" element={<Index />} />
          <Route path="/budget" element={<Budget />} />
          <Route path="/budget/category/:category" element={<CategoryExpenses />} />
          <Route path="/budget/metric/:metric" element={<MetricDetails />} />
          <Route path="/investments" element={<Investments />} />
          <Route path="/investments/analytics" element={<PortfolioAnalytics />} />
          <Route path="/investments/security/:ticker" element={<SecurityDetails />} />
          <Route path="/register" element={<Register />} />
          <Route path="/login" element={<Login />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          {/* ADD ALL CUSTOM ROUTES ABOVE THE CATCH-ALL "*" ROUTE */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </div>
    );
  }

  return (
    <div className="dashboard-bg h-screen overflow-hidden">
      <Sidebar />
      <BottomNav />
      <main className="lg:ml-[264px] h-screen overflow-y-auto dashboard-scroll p-4 lg:p-6 pb-20 lg:pb-6">
        <Routes>
          <Route path="/" element={<Index />} />
          <Route path="/budget" element={<Budget />} />
          <Route path="/budget/category/:category" element={<CategoryExpenses />} />
          <Route path="/budget/metric/:metric" element={<MetricDetails />} />
          <Route path="/investments" element={<Investments />} />
          <Route path="/investments/analytics" element={<PortfolioAnalytics />} />
          <Route path="/investments/security/:ticker" element={<SecurityDetails />} />
          <Route path="/register" element={<Register />} />
          <Route path="/login" element={<Login />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          {/* ADD ALL CUSTOM ROUTES ABOVE THE CATCH-ALL "*" ROUTE */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
    </div>
  );
};

const App = () => (
  <Sentry.ErrorBoundary fallback={ErrorFallback} showDialog>
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <AuthProvider>
          <HealthGate>
            <Toaster />
            <Sonner />
            <BrowserRouter>
              <AppLayout />
            </BrowserRouter>
          </HealthGate>
        </AuthProvider>
      </TooltipProvider>
    </QueryClientProvider>
  </Sentry.ErrorBoundary>
);

export default App;
