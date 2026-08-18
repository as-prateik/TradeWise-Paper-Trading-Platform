/**
 * Typed mirrors of the backend DTOs (see backend/docs/openapi.yaml).
 *
 * Money note: the backend sends BigDecimal values as JSON numbers. We treat them
 * as display values only — the server is the single authority for money
 * arithmetic. The one client-side calculation is the order-ticket cost preview,
 * which is explicitly labelled an estimate.
 */

export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'MARKET';
export type OrderStatus = 'PENDING' | 'EXECUTED' | 'CANCELLED' | 'REJECTED' | 'EXPIRED';
export type RejectionReason = 'INSUFFICIENT_FUNDS' | 'INSUFFICIENT_SHARES' | 'MARKET_CLOSED';
export type MarketStatus = 'OPEN' | 'PRE_OPEN' | 'CLOSED' | 'HOLIDAY';
export type TimeRange = 'ONE_DAY' | 'ONE_WEEK' | 'ONE_MONTH' | 'THREE_MONTHS' | 'ONE_YEAR';
export type TransactionType = 'SEED' | 'TRADE_DEBIT' | 'TRADE_CREDIT';

export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'EMAIL_ALREADY_REGISTERED'
  | 'INVALID_CREDENTIALS'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'RESOURCE_NOT_FOUND'
  | 'SYMBOL_NOT_FOUND'
  | 'CONCURRENT_MODIFICATION'
  | 'INTERNAL_ERROR'
  | 'NETWORK_ERROR';

export interface FieldValidationError {
  field: string;
  message: string;
}

/** The backend's single error envelope. */
export interface ErrorResponse {
  timestamp: string;
  status: number;
  errorCode: ErrorCode;
  message: string;
  path: string;
  fieldErrors?: FieldValidationError[] | null;
}

export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserResponse;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface WalletResponse {
  balance: number;
  updatedAt: string;
}

export interface StockSearchResult {
  symbol: string;
  companyName: string;
  exchange: string;
}

export interface Quote {
  symbol: string;
  companyName: string;
  price: number;
  dayOpen: number;
  dayHigh: number;
  dayLow: number;
  previousClose: number;
  changeAbsolute: number;
  changePercent: number;
  volume: number;
  asOf: string;
}

export interface Candle {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface HistoricalData {
  symbol: string;
  range: TimeRange;
  candles: Candle[];
}

export interface MarketStatusResponse {
  status: MarketStatus;
  asOf: string;
}

export interface PlaceOrderRequest {
  symbol: string;
  side: OrderSide;
  type: OrderType;
  quantity: number;
}

export interface OrderResponse {
  id: string;
  symbol: string;
  side: OrderSide;
  type: OrderType;
  quantity: number;
  status: OrderStatus;
  executedPrice: number | null;
  executedAt: string | null;
  rejectionReason: RejectionReason | string | null;
  createdAt: string;
}

export interface TradeResponse {
  id: string;
  orderId: string;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
  grossAmount: number;
  executedAt: string;
}

export interface TransactionResponse {
  id: string;
  type: TransactionType;
  amount: number;
  balanceAfter: number;
  referenceOrderId: string | null;
  description: string;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface HoldingView {
  symbol: string;
  companyName: string;
  quantity: number;
  averagePrice: number;
  investedValue: number;
  currentPrice: number;
  marketValue: number;
  unrealizedPnl: number;
  unrealizedPnlPercent: number;
  realizedPnl: number;
  allocationPercent: number;
}

export interface PortfolioTotals {
  investedValue: number;
  marketValue: number;
  unrealizedPnl: number;
  realizedPnl: number;
}

export interface PortfolioResponse {
  holdings: HoldingView[];
  totals: PortfolioTotals;
}

export interface DashboardResponse {
  cashBalance: number;
  portfolioMarketValue: number;
  totalAccountValue: number;
  investedValue: number;
  unrealizedPnl: number;
  realizedPnl: number;
  openPositionCount: number;
  marketStatus: MarketStatus;
  recentOrders: OrderResponse[];
}
