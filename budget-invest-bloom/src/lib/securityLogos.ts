// Tickers we ship a local logo for (PNG in public/logos/{TICKER}.png).
// Anything else falls back to initials in SecurityLogo.
const LOCAL_LOGOS = new Set([
  'AFKS','AFLT','AKRN','ALRS','ASTR','BELU','BSPB','CBOM','CHMF','DELI',
  'ENPG','ETLN','FEES','FESH','FLOT','GAZP','GEMC','GMKN','HEAD','HYDR',
  'IRAO','LENT','LKOH','MAGN','MDMG','MGNT','MOEX','MTLR','MTLRP','MTSS',
  'MVID','NLMK','NVTK','OZON','PHOR','PIKK','PLZL','POSI','RASP','ROSN',
  'RTKM','RTKMP','RUAL','SBER','SBERP','SELG','SGZH','SMLT','SNGS','SNGSP',
  'T','TATN','TATNP','TRNFP','UPRO','VKCO','VTBR','WUSH','X5','YDEX',
]);

export function localLogoUrl(ticker: string): string | null {
  const t = ticker.toUpperCase();
  return LOCAL_LOGOS.has(t) ? `/logos/${t}.png` : null;
}
