import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import * as Sentry from "@sentry/react";
import { AuthProvider } from "@/contexts/AuthContext";
import Navigation from "@/components/Navigation";
import Index from "./pages/Index";
import Budget from "./pages/Budget";
import Investments from "./pages/Investments";
import CategoryExpenses from "./pages/CategoryExpenses";
import MetricDetails from "./pages/MetricDetails";
import Register from "./pages/Register";
import Login from "./pages/Login";
import ForgotPassword from "./pages/ForgotPassword";
import NotFound from "./pages/NotFound";

const queryClient = new QueryClient();

// Fallback компонент для Error Boundary
const ErrorFallback = ({ resetError }: { resetError: () => void }) => (
  <div className="min-h-screen flex items-center justify-center bg-gradient-background">
    <div className="max-w-md p-8 bg-card rounded-lg shadow-lg text-center">
      <h1 className="text-2xl font-bold text-destructive mb-4">Что-то пошло не так</h1>
      <p className="text-muted-foreground mb-6">
        Произошла непредвиденная ошибка. Мы уже получили уведомление и работаем над исправлением.
      </p>
      <button
        onClick={resetError}
        className="px-4 py-2 bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors"
      >
        Попробовать снова
      </button>
    </div>
  </div>
);

const App = () => (
  <Sentry.ErrorBoundary fallback={ErrorFallback} showDialog>
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <AuthProvider>
          <Toaster />
          <Sonner />
          <BrowserRouter>
            <div className="min-h-screen bg-gradient-background">
              <Navigation />
              <Routes>
                <Route path="/" element={<Index />} />
                <Route path="/budget" element={<Budget />} />
                <Route path="/budget/category/:category" element={<CategoryExpenses />} />
                <Route path="/budget/metric/:metric" element={<MetricDetails />} />
                <Route path="/investments" element={<Investments />} />
                <Route path="/register" element={<Register />} />
                <Route path="/login" element={<Login />} />
                <Route path="/forgot-password" element={<ForgotPassword />} />
                {/* ADD ALL CUSTOM ROUTES ABOVE THE CATCH-ALL "*" ROUTE */}
                <Route path="*" element={<NotFound />} />
              </Routes>
            </div>
          </BrowserRouter>
        </AuthProvider>
      </TooltipProvider>
    </QueryClientProvider>
  </Sentry.ErrorBoundary>
);

export default App;
