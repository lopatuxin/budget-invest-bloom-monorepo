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
  currentPrice: number | null; // filled in phase 4
  pnl: number | null; // filled in phase 4
  updatedAt: string;
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
