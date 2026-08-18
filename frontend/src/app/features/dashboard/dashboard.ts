import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api/api';
import { DashboardResponse } from '../../core/api-types';
import { AppError } from '../../core/app-error';
import { AuthStore } from '../../core/auth/auth-store';
import { MoneyPipe, Percent2Pipe } from '../../core/util/money-pipe';
import { pollWhileVisible } from '../../core/util/polling';
import { MarketStatusChip } from '../../shared/market-status-chip';
import { OrderStatusChip } from '../../shared/order-status-chip';
import { StateBlock } from '../../shared/state-block';

@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, MoneyPipe, Percent2Pipe, MarketStatusChip, OrderStatusChip, StateBlock],
  template: `
    <div class="spread head">
      <div>
        <h1>Good to see you, {{ firstName() }}</h1>
        <p class="muted small">Your paper trading account at a glance.</p>
      </div>
      <div class="row">
        @if (data(); as dashboard) {
          <app-market-status-chip [status]="dashboard.marketStatus" />
        }
        <a class="btn btn-primary" routerLink="/trade">Place a trade</a>
      </div>
    </div>

    <app-state-block
      [loading]="loading()"
      [error]="error()"
      [hasData]="data() !== null"
      (retry)="load()"
    >
      @if (data(); as dashboard) {
        <div class="tiles">
          <div class="card tile">
            <span class="label">Total account value</span>
            <span class="figure num">{{ dashboard.totalAccountValue | money }}</span>
            <span class="small muted">Cash + holdings at market</span>
          </div>

          <div class="card tile">
            <span class="label">Cash available</span>
            <span class="figure num">{{ dashboard.cashBalance | money }}</span>
            <span class="small muted">Buying power</span>
          </div>

          <div class="card tile">
            <span class="label">Unrealised P&amp;L</span>
            <span class="figure num" [class.up]="dashboard.unrealizedPnl > 0" [class.down]="dashboard.unrealizedPnl < 0">
              {{ dashboard.unrealizedPnl | money: 'signed' }}
            </span>
            <span class="small muted">
              On {{ dashboard.investedValue | money }} invested
              @if (dashboard.investedValue > 0) {
                ({{ (dashboard.unrealizedPnl / dashboard.investedValue) * 100 | percent2: true }})
              }
            </span>
          </div>

          <div class="card tile">
            <span class="label">Realised P&amp;L</span>
            <span class="figure num" [class.up]="dashboard.realizedPnl > 0" [class.down]="dashboard.realizedPnl < 0">
              {{ dashboard.realizedPnl | money: 'signed' }}
            </span>
            <span class="small muted">Booked on closed quantity</span>
          </div>
        </div>

        <div class="lower">
          <section class="card">
            <div class="card-head">
              <h2>Recent orders</h2>
              <a class="small" routerLink="/orders">View all</a>
            </div>

            @if (dashboard.recentOrders.length === 0) {
              <p class="muted small empty">
                No orders yet. <a routerLink="/trade">Place your first trade</a> to get started.
              </p>
            } @else {
              <div class="table-scroll">
                <table class="table">
                  <thead>
                    <tr>
                      <th>Symbol</th>
                      <th>Side</th>
                      <th class="right">Qty</th>
                      <th class="right">Price</th>
                      <th>Status</th>
                      <th class="right">Placed</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (order of dashboard.recentOrders; track order.id) {
                      <tr>
                        <td><strong>{{ order.symbol }}</strong></td>
                        <td>
                          <span class="chip" [class.chip-up]="order.side === 'BUY'" [class.chip-down]="order.side === 'SELL'">
                            {{ order.side }}
                          </span>
                        </td>
                        <td class="right num">{{ order.quantity }}</td>
                        <td class="right num">{{ order.executedPrice | money }}</td>
                        <td>
                          <app-order-status-chip [status]="order.status" [reason]="order.rejectionReason" />
                        </td>
                        <td class="right num small muted">{{ order.createdAt | date: 'dd MMM, HH:mm' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </section>

          <section class="card side">
            <div class="card-head"><h2>Positions</h2></div>
            <div class="stat">
              <span class="big num">{{ dashboard.openPositionCount }}</span>
              <span class="muted small">open {{ dashboard.openPositionCount === 1 ? 'position' : 'positions' }}</span>
            </div>
            <hr class="divider" />
            <div class="spread small">
              <span class="muted">Holdings at market</span>
              <span class="num">{{ dashboard.portfolioMarketValue | money }}</span>
            </div>
            <div class="spread small">
              <span class="muted">Invested</span>
              <span class="num">{{ dashboard.investedValue | money }}</span>
            </div>
            <a class="btn btn-sm view" routerLink="/portfolio">Open portfolio</a>
          </section>
        </div>

        <p class="muted small refresh">Refreshed {{ updatedAt() | date: 'HH:mm:ss' }}</p>
      }
    </app-state-block>
  `,
  styles: `
    .head { margin-bottom: 20px; flex-wrap: wrap; }
    .head h1 { margin-bottom: 2px; }
    .head p { margin: 0; }

    .tiles {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
      gap: var(--gap);
      margin-bottom: var(--gap);
    }
    .tile { display: flex; flex-direction: column; gap: 5px; }
    .figure { font-size: 24px; font-weight: 600; letter-spacing: -0.02em; }

    .lower { display: grid; grid-template-columns: 1fr 300px; gap: var(--gap); align-items: start; }
    .side { display: flex; flex-direction: column; }
    .stat { display: flex; align-items: baseline; gap: 8px; }
    .big { font-size: 30px; font-weight: 600; }
    .view { margin-top: 14px; text-align: center; }
    .empty { padding: 18px 0; }
    .refresh { margin: 14px 0 0; text-align: right; }

    @media (max-width: 900px) {
      .lower { grid-template-columns: 1fr; }
    }
  `,
})
export class Dashboard {
  private readonly api = inject(Api);
  private readonly authStore = inject(AuthStore);

  protected readonly data = signal<DashboardResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<AppError | null>(null);
  protected readonly updatedAt = signal<Date>(new Date());

  protected readonly firstName = computed(
    () => this.authStore.user()?.fullName.split(' ')[0] ?? 'trader',
  );

  constructor() {
    // Poll rather than push: Phase 1 has no WebSocket (documented decision).
    pollWhileVisible(() => this.api.dashboard()).subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        this.updatedAt.set(new Date());
        this.loading.set(false);
        this.error.set(null);
      },
      error: (appError: AppError) => {
        this.error.set(appError);
        this.loading.set(false);
      },
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.dashboard().subscribe({
      next: (dashboard) => {
        this.data.set(dashboard);
        this.updatedAt.set(new Date());
        this.loading.set(false);
      },
      error: (appError: AppError) => {
        this.error.set(appError);
        this.loading.set(false);
      },
    });
  }
}
