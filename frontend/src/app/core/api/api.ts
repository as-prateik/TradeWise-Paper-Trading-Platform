import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AuthResponse,
  DashboardResponse,
  HistoricalData,
  LoginRequest,
  MarketStatusResponse,
  OrderResponse,
  PageResponse,
  PlaceOrderRequest,
  PortfolioResponse,
  Quote,
  RegisterRequest,
  StockSearchResult,
  TimeRange,
  TradeResponse,
  TransactionResponse,
  UserResponse,
  WalletResponse,
} from '../api-types';

const BASE = '/api/v1';

/**
 * The single typed gateway to the backend. Every method maps 1:1 to a
 * documented endpoint; nothing else in the app talks to HttpClient directly.
 */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  // ---- auth ----
  register(body: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${BASE}/auth/register`, body);
  }

  login(body: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${BASE}/auth/login`, body);
  }

  // ---- user & wallet ----
  currentUser(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${BASE}/users/me`);
  }

  wallet(): Observable<WalletResponse> {
    return this.http.get<WalletResponse>(`${BASE}/wallets/me`);
  }

  transactions(page: number, size = 20): Observable<PageResponse<TransactionResponse>> {
    return this.http.get<PageResponse<TransactionResponse>>(`${BASE}/transactions`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  // ---- market data ----
  search(query: string): Observable<StockSearchResult[]> {
    return this.http.get<StockSearchResult[]>(`${BASE}/market/search`, {
      params: new HttpParams().set('query', query),
    });
  }

  quote(symbol: string): Observable<Quote> {
    return this.http.get<Quote>(`${BASE}/market/quotes/${encodeURIComponent(symbol)}`);
  }

  history(symbol: string, range: TimeRange): Observable<HistoricalData> {
    return this.http.get<HistoricalData>(`${BASE}/market/history/${encodeURIComponent(symbol)}`, {
      params: new HttpParams().set('range', range),
    });
  }

  marketStatus(): Observable<MarketStatusResponse> {
    return this.http.get<MarketStatusResponse>(`${BASE}/market/status`);
  }

  // ---- trading ----
  /**
   * The Idempotency-Key is required by the backend: replaying the same key
   * returns the original order instead of executing twice.
   */
  placeOrder(body: PlaceOrderRequest, idempotencyKey: string): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(`${BASE}/orders`, body, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  order(orderId: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${BASE}/orders/${orderId}`);
  }

  orders(page: number, size = 20): Observable<PageResponse<OrderResponse>> {
    return this.http.get<PageResponse<OrderResponse>>(`${BASE}/orders`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  trades(page: number, size = 20): Observable<PageResponse<TradeResponse>> {
    return this.http.get<PageResponse<TradeResponse>>(`${BASE}/trades`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  // ---- aggregates ----
  portfolio(): Observable<PortfolioResponse> {
    return this.http.get<PortfolioResponse>(`${BASE}/portfolio`);
  }

  dashboard(): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(`${BASE}/dashboard`);
  }
}
