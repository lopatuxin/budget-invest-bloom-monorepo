import type { ApiResponse } from './common';

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
}

export interface ProjectionResult {
  startValue: number;
  portfolioWeightedAnnualReturn: number;
  monthlyReturn: number;
  series: ProjectionPoint[];
  pendingHistoryTickers: string[];
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

// Re-exported from common for backward compatibility
export type { ApiResponse } from './common';
