import type { ApiResponse } from './budget';

export type TransactionType = 'BUY' | 'SELL';
export type SecurityType = 'STOCK' | 'BOND' | 'ETF' | 'OFZ';

export interface TransactionResponse {
  id: string;
  ticker: string;
  securityName: string;
  type: TransactionType;
  quantity: number;
  price: number;
  executedAt: string;
  createdAt: string;
}

export interface PositionResponse {
  id: string;
  ticker: string;
  securityName: string;
  securityType: SecurityType;
  sector: string | null;
  quantity: number;
  averagePrice: number;
  totalCost: number;
  currentPrice: number | null;
  pnl: number | null;
  updatedAt: string;
}

export interface PortfolioOverview {
  totalValue: number;
  totalCost: number;
  totalPnl: number;
  dailyPnl: number;
  assetsCount: number;
  dividends12m: number;
}

export interface PortfolioPageResponse {
  overview: PortfolioOverview;
  positions: PositionResponse[];
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
  lookbackYears: number;
  overrides: Record<string, number>;
}

export type { ApiResponse };
