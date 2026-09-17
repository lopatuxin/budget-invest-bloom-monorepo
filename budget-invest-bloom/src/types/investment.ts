export type TransactionType = 'BUY' | 'SELL' | 'REDEMPTION';
export type SecurityType = 'STOCK' | 'BOND' | 'ETF' | 'OFZ';
export type SecurityHistoryStatus = 'PENDING' | 'READY';
export type PortfolioSort = 'WEIGHT' | 'PNL' | 'DAY';

export interface TransactionResponse {
  id: string;
  ticker: string;
  securityName: string;
  type: TransactionType;
  quantity: number;
  price: number;
  amount: number;
  executedAt: string;
  createdAt: string;
}

export interface PositionResponse {
  id: string;
  ticker: string;
  securityName: string;
  securityType: SecurityType;
  sector: string | null;
  historyStatus: SecurityHistoryStatus;
  quantity: number;
  averagePrice: number;
  totalCost: number;
  currentPrice: number | null;
  previousClose: number | null;
  currentValue: number | null;
  pnl: number | null;
  pnlPercent: number | null;
  dailyChangeAmount: number | null;
  dailyChangePercent: number | null;
  weightPercent: number | null;
  updatedAt: string;
}

export interface PortfolioOverview {
  totalValue: number;
  totalCost: number;
  totalPnl: number;
  totalPnlPercent: number | null;
  dailyPnl: number;
  dailyPnlPercent: number | null;
  assetsCount: number;
  sectorsCount: number;
  dividends12m: number;
  dividendYieldPercent: number | null;
  dividendTaxRatePercent: number;
  pricesAsOf: string | null;
  pricesStale: boolean;
  unpricedCount: number;
  // false when TINVEST_TOKEN is unset or dividends have never synced — see
  // Фазы Финансов/Фаза-04-дивиденды-из-t-invest.md p.24
  dividendsSourceConfigured: boolean;
}

// Shared shape for both allocation lists: byType entries carry securityType,
// bySector entries carry sector — the other key is simply absent. securityType
// is explicitly null for a position whose type the exchange never classified
// (the backend buckets it separately instead of dropping it — see
// securityTypeLabel). percent is null when totalValue is 0 (no priced
// positions at all — exchange down with no snapshot in the database), same as
// every per-position share below.
export interface AllocationItem {
  securityType?: SecurityType | null;
  sector?: string;
  value: number;
  percent: number | null;
  assetsCount: number;
}

export interface PortfolioAllocation {
  byType: AllocationItem[];
  bySector: AllocationItem[];
}

export interface SectorGroup {
  sector: string | null;
  value: number;
  percent: number | null;
  assetsCount: number;
  positions: PositionResponse[];
}

export interface PositionGroup {
  securityType: SecurityType | null;
  value: number;
  percent: number | null;
  assetsCount: number;
  sectors: SectorGroup[];
}

// A payout's underlying instrument: a stock/ETF dividend or a bond/OFZ coupon
// (plan Фазы Финансов/Фаза-10-купоны-и-цены-облигаций.md p.8, p.91). Named
// `payoutKind` everywhere it sits next to an existing `kind` discriminant
// (SecurityEvent) to avoid colliding with it; plain `kind` elsewhere.
export type PayoutKind = 'DIVIDEND' | 'COUPON';

export interface UpcomingDividend {
  ticker: string;
  securityName: string;
  recordDate: string;
  paymentDate: string | null;
  amountPerShare: number;
  quantity: number;
  totalAmount: number;
  currency: string;
  kind: PayoutKind;
}

export interface PortfolioPageResponse {
  overview: PortfolioOverview;
  allocation: PortfolioAllocation;
  groups: PositionGroup[];
  positions: PositionResponse[];
  upcomingDividends: UpcomingDividend[];
  recentDividends: UpcomingDividend[];
  recentTransactions: TransactionResponse[];
  transactionsTotal: number;
}

export interface MoexSecuritySearchItem {
  ticker: string;
  boardId: string;
  name: string;
  securityType: SecurityType;
  sector: string | null;
  currency: string | null;
}

export interface CreateTransactionRequest {
  ticker: string;
  type: TransactionType;
  securityType: SecurityType;
  quantity: number;
  price: number;
  executedAt: string;
}

export interface PortfolioValuePoint {
  date: string;
  value: number;
}

export interface PricePoint {
  date: string;
  open: number;
  close: number;
  high: number;
  low: number;
  volume: number;
}

export interface SeriesResponse<T> {
  series: T[];
  historyPending: boolean;
  pendingTickers: string[];
}

// value-history's own response shape: adds staleness flags on top of
// SeriesResponse, absent from price-history's response. Both fields are
// optional so an old backend response (pre-rollout) still type-checks.
export interface PortfolioValueHistoryResponse extends SeriesResponse<PortfolioValuePoint> {
  pricesStale?: boolean;
  staleTickers?: string[];
}

export interface ProjectionPoint {
  month: number;
  date: string;
  value: number;
  deposit: number;
  withdrawal: number;
  // Starting value plus deposits minus withdrawals up to this point — lets the
  // chart draw a "contributed" line alongside the grown value.
  contributed: number;
}

// One security's contribution to the portfolio's weighted annual return (plan p.13, p.15):
// priceGrowthPercent/payoutYieldPercent are that security's own annual rates (not yet weighted
// by weightPercent), scale 1 like ProjectionResult's own fields. payoutKind is 'NONE' when the
// security has never paid — lastPayoutYear is then absent.
export interface ProjectionBreakdownItem {
  ticker: string;
  securityName: string;
  securityType: SecurityType;
  weightPercent: number;
  priceGrowthPercent: number;
  payoutYieldPercent: number;
  payoutKind: PayoutKind | 'NONE';
  yearsCounted: number;
  lastPayoutYear?: number;
  historyPending: boolean;
}

export interface ProjectionResult {
  startValue: number;
  portfolioWeightedAnnualReturn: number;
  monthlyReturn: number;
  // Portfolio-wide split of portfolioWeightedAnnualReturn into price growth and after-tax
  // payouts (plan p.13): priceGrowthPercent + payoutYieldPercent === portfolioWeightedAnnualReturn * 100.
  priceGrowthPercent: number;
  payoutYieldPercent: number;
  breakdown: ProjectionBreakdownItem[];
  series: ProjectionPoint[];
  pendingHistoryTickers: string[];
  contributedTotal: number;
  earned: number;
}

export interface ProjectionRequest {
  horizonMonths: number;
  monthlyDeposit: number;
  withdrawalRatePerYear: number;
  // key: ticker symbol (e.g. 'SBER')
  overrides: Record<string, number>;
}

export type DividendSource = 'MOEX' | 'TINVEST' | 'MANUAL';

// CANCELLED exists in the backend enum but nothing in this task sets it
// automatically — see Фазы Финансов/Фаза-04-дивиденды-из-t-invest.md scope.
export type DividendStatus = 'ANNOUNCED' | 'PAID' | 'CANCELLED';

export interface SecurityDividend {
  id: string;
  recordDate: string;
  paymentDate: string | null;
  amountPerShare: number;
  currency: string;
  status: DividendStatus;
  source: DividendSource;
}

export interface CreateManualDividendRequest {
  ticker: string;
  recordDate: string;
  paymentDate?: string;
  amountPerShare: number;
  currency?: string;
}

// --- Security page (/investments/security/:ticker), POST /api/investment/securities/page ---
// See Фазы Финансов/Фаза-08-страница-бумаги-и-прогноз.md "API" section for the full response shape.
// Optional (?) fields come from DTOs serialised with @JsonInclude(NON_NULL): when empty they
// are absent, i.e. undefined rather than null — check them with `!= null`, never `!== null`.

export interface SecurityPageSecurity {
  ticker: string;
  name: string;
  securityType: SecurityType;
  sector?: string;
  // Empty until the startup backfill has read the trading board from MOEX
  boardId?: string;
  historyStatus: SecurityHistoryStatus;
}

// Serialised without NON_NULL: previousClose and dailyChangePercent arrive as null when the
// exchange snapshot has no previous close. accruedInterest and nominalDefaulted are BOND/OFZ-only
// and NON_NULL (absent — undefined — for a stock, or a bond with no known accrued interest);
// see BondPricing (plan p.3, p.17): current is already the ruble price, accrued interest on top.
export interface SecurityPagePrice {
  current: number;
  previousClose: number | null;
  dailyChangePercent: number | null;
  asOf: string;
  stale: boolean;
  accruedInterest?: number;
  nominalDefaulted?: boolean;
}

// Mirrors PositionResponse's price-dependent fields: null whenever the exchange
// snapshot is unavailable, even though the position itself is open.
export interface SecurityPagePosition {
  quantity: number;
  averagePrice: number;
  totalCost: number;
  currentValue: number | null;
  pnl: number | null;
  pnlPercent: number | null;
  weightPercent: number | null;
}

export interface SecurityNextDividend {
  recordDate: string;
  paymentDate?: string;
  amountPerShare: number;
  quantity: number;
  netAmount: number;
  currency: string;
  kind: PayoutKind;
}

export interface SecurityPageDividends {
  total12m: number;
  totalAll: number;
  yield12mPercent?: number;
  next?: SecurityNextDividend;
}

export interface SecurityPageResult {
  pricePnl: number;
  realizedPnl: number;
  dividendsAll: number;
  total: number;
  investedAll: number;
  totalPercent?: number;
}

export interface SecurityMarker {
  date: string;
  kind: TransactionType;
  quantity: number;
  price: number;
}

export interface SecurityPositionAfter {
  quantity: number;
  invested: number;
  averagePrice: number;
}

export interface SecurityBuyEvent {
  kind: 'BUY';
  date: string;
  transactionId: string;
  quantity: number;
  price: number;
  amount: number;
  positionAfter: SecurityPositionAfter;
  first?: boolean;
}

export interface SecuritySellEvent {
  kind: 'SELL';
  date: string;
  transactionId: string;
  quantity: number;
  price: number;
  amount: number;
  realizedPnl: number;
  positionAfter: SecurityPositionAfter;
}

export interface SecurityRedemptionEvent {
  kind: 'REDEMPTION';
  date: string;
  transactionId: string;
  quantity: number;
  price: number;
  amount: number;
  realizedPnl: number;
  positionAfter: SecurityPositionAfter;
}

export interface SecurityDividendPaidEvent {
  kind: 'DIVIDEND_PAID';
  date: string;
  dividendId: string;
  amountPerShare: number;
  quantity: number;
  netAmount: number;
  currency: string;
  source: DividendSource;
  // Named payoutKind, not kind, so it doesn't collide with this event's own
  // `kind` discriminant (BUY/SELL/DIVIDEND_PAID/DIVIDEND_UPCOMING).
  payoutKind: PayoutKind;
}

export interface SecurityDividendUpcomingEvent {
  kind: 'DIVIDEND_UPCOMING';
  date: string;
  dividendId: string;
  amountPerShare: number;
  quantity: number;
  netAmount: number;
  currency: string;
  paymentDate?: string;
  source: DividendSource;
  payoutKind: PayoutKind;
}

export type SecurityEvent =
  | SecurityBuyEvent
  | SecuritySellEvent
  | SecurityRedemptionEvent
  | SecurityDividendPaidEvent
  | SecurityDividendUpcomingEvent;

export interface SecurityPageResponse {
  security: SecurityPageSecurity;
  price?: SecurityPagePrice;
  position?: SecurityPagePosition;
  dividends: SecurityPageDividends;
  result: SecurityPageResult;
  transactionsCount: number;
  buysCount: number;
  sellsCount: number;
  markers: SecurityMarker[];
  events: SecurityEvent[];
}

// Re-exported from common for backward compatibility
export type { ApiResponse } from './common';
