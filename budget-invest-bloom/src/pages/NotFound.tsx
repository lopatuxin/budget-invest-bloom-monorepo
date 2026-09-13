import { Link, useLocation } from "react-router-dom";
import { useEffect } from "react";

const NotFound = () => {
  const location = useLocation();

  useEffect(() => {
    console.warn(
      "404 Error: User attempted to access non-existent route:",
      location.pathname
    );
  }, [location.pathname]);

  return (
    <div className="min-h-[60vh] flex items-center justify-center">
      <div className="flex flex-col items-center gap-2 text-center">
        <span className="font-display text-[72px] leading-none text-app-text-dim">404</span>
        <span className="font-display text-[26px] text-app-text">Такой страницы нет</span>
        <span className="text-[13px] text-app-text-muted">Возможно, ссылка устарела</span>
        <Link to="/" className="mt-1.5 text-[13px] text-app-accent hover:underline">
          К обзору
        </Link>
      </div>
    </div>
  );
};

export default NotFound;
