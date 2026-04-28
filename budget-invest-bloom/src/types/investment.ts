import type { ApiResponse } from './budget';

export type TransactionType = 'BUY' | 'SELL';
export type SecurityType = 'STOCK' | 'BOND';

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
}

export interface MoexSecuritySearchItem {
  ticker: string;
  boardId: string;
  name: string;
  securityType: 'STOCK' | 'BOND';
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

export type { ApiResponse };
