import { Link, useLocation } from "react-router-dom";
import { useEffect } from "react";

const NotFound = () => {
  const location = useLocation();

  useEffect(() => {
    console.error(
      "404 Error: User attempted to access non-existent route:",
      location.pathname
    );
  }, [location.pathname]);

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center">
      <div className="glass-card p-12 text-center max-w-md">
        <h1 className="text-6xl font-bold text-emerald-400 mb-4">404</h1>
        <p className="text-xl text-dashboard-text-muted mb-6">Страница не найдена</p>
        <Link to="/" className="text-emerald-400 hover:text-emerald-300 underline">
          Вернуться на главную
        </Link>
      </div>
    </div>
  );
};

export default NotFound;
