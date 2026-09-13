export type TransactionType = 'BUY' | 'SELL';
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
  // docs/plans/dividends-tinvest.md p.24
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

export interface UpcomingDividend {
  ticker: string;
  securityName: string;
  recordDate: string;
  paymentDate: string | null;
  amountPerShare: number;
  quantity: number;
  totalAmount: number;
  currency: string;
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

export interface ProjectionResult {
  startValue: number;
  portfolioWeightedAnnualReturn: number;
  monthlyReturn: number;
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
// automatically — see docs/plans/dividends-tinvest.md scope.
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
// See docs/plans/security-page-redesign.md "API" section for the full response shape.
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
// exchange snapshot has no previous close.
export interface SecurityPagePrice {
  current: number;
  previousClose: number | null;
  dailyChangePercent: number | null;
  asOf: string;
  stale: boolean;
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

export interface SecurityDividendPaidEvent {
  kind: 'DIVIDEND_PAID';
  date: string;
  dividendId: string;
  amountPerShare: number;
  quantity: number;
  netAmount: number;
  currency: string;
  source: DividendSource;
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
}

export type SecurityEvent =
  | SecurityBuyEvent
  | SecuritySellEvent
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
