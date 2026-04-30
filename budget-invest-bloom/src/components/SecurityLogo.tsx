import { useState, useEffect } from 'react';

const PALETTE = [
  'from-blue-500 to-blue-700',
  'from-emerald-500 to-emerald-700',
  'from-violet-500 to-violet-700',
  'from-orange-500 to-orange-700',
  'from-pink-500 to-pink-700',
  'from-teal-500 to-teal-700',
];

function tickerColor(ticker: string) {
  return PALETTE[ticker.charCodeAt(0) % PALETTE.length];
}

interface SecurityLogoProps {
  ticker: string;
  size?: number;
}

export function SecurityLogo({ ticker, size = 24 }: SecurityLogoProps) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [ticker]);

  const initials = ticker.slice(0, 2).toUpperCase();
  const style = { width: size, height: size, minWidth: size };

  if (!failed) {
    return (
      <img
        src={`https://invest-brands.cdn-tinkoff.ru/${ticker}x160.png`}
        alt={ticker}
        style={style}
        className="rounded-full object-contain bg-white/5"
        onError={() => setFailed(true)}
      />
    );
  }

  return (
    <div
      role="img"
      aria-label={ticker}
      style={{ width: size, height: size, minWidth: size, fontSize: size * 0.35 }}
      className={`rounded-full bg-gradient-to-br ${tickerColor(ticker)} flex items-center justify-center text-white font-bold`}
    >
      {initials}
    </div>
  );
}
