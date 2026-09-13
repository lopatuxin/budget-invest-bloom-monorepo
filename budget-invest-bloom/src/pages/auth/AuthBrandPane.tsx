import { type ReactNode } from 'react';
import { Sprout } from 'lucide-react';

const PROMISES = [
  'Расходы сравниваются с вашей нормой, а не с лимитами',
  'Год против прошлого по каждой категории',
  'Портфель, дивиденды и прогноз в одном месте',
];

const TILES = [
  { label: 'Расходы за месяц', value: '34 200 ₽', hint: '−8% к обычному' },
  { label: 'Портфель', value: '862 400 ₽', hint: '+12,4% за год' },
  { label: 'Личная инфляция', value: '+5,3%', hint: '2026 к 2025' },
];

function BrandMark({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-center gap-2.5">
      <div className="w-[34px] h-[34px] rounded-[10px] bg-app-bg flex items-center justify-center flex-shrink-0">
        <Sprout aria-hidden="true" className="w-[18px] h-[18px] text-app-accent" />
      </div>
      <span className="text-[13px] font-semibold tracking-wide">{children}</span>
    </div>
  );
}

// Illustrative panel for the auth split layout — statically written copy and numbers,
// nothing here comes from the backend. Desktop shows the full promise panel; below
// 1024px only the compact top band (mark + headline, no tiles) is visible.
export function AuthBrandPane() {
  return (
    <>
      <div className="hidden lg:flex flex-col justify-between bg-app-accent text-app-bg px-16 py-14">
        <BrandMark>Мои финансы</BrandMark>

        <div className="flex flex-col gap-7">
          <span className="font-display text-[52px] leading-[1.05]">
            Бюджет, инвестиции
            <br />
            и всё, что между ними
          </span>
          <div className="flex flex-col gap-3.5 text-[15px] text-app-bg/85">
            {PROMISES.map((line) => (
              <span key={line}>{line}</span>
            ))}
          </div>
          <div className="grid grid-cols-3 gap-3">
            {TILES.map((tile) => (
              <div key={tile.label} className="rounded-[10px] p-3 bg-app-bg/[0.12]">
                <div className="text-[11px] text-app-bg/80">{tile.label}</div>
                <div className="font-mono text-[18px] font-semibold">{tile.value}</div>
                <div className="text-[11px] text-app-bg/80">{tile.hint}</div>
              </div>
            ))}
          </div>
        </div>

        <span className="text-[12px] text-app-bg/70">личное приложение, без рекламы и подписок</span>
      </div>

      <div className="lg:hidden bg-app-accent text-app-bg pt-[52px] px-5 pb-6 flex flex-col gap-3.5">
        <BrandMark>Мои финансы</BrandMark>
        <span className="font-display text-[26px] leading-[1.1]">Бюджет, инвестиции и всё, что между ними</span>
      </div>
    </>
  );
}
