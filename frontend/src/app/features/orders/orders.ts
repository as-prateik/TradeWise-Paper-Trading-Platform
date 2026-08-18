import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api/api';
import { OrderResponse, PageResponse, TradeResponse } from '../../core/api-types';
import { AppError } from '../../core/app-error';
import { MoneyPipe } from '../../core/util/money-pipe';
import { OrderStatusChip, REJECTION_TEXT } from '../../shared/order-status-chip';
import { StateBlock } from '../../shared/state-block';

type Tab = 'orders' | 'trades';

@Component({
  selector: 'app-orders',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, MoneyPipe, OrderStatusChip, StateBlock],
  template: `
    <div class="spread head">
      <div>
        <h1>Activity</h1>
        <p class="muted small">
          Orders are every submission and its outcome. Trades are executions only — the two are
          deliberately different records.
        </p>
      </div>
      <a class="btn btn-primary" routerLink="/trade">Place a trade</a>
    </div>

    <div class="tabs">
      <button type="button" class="btn btn-sm" [class.active]="tab() === 'orders'" (click)="switchTab('orders')">
        Orders
      </button>
      <button type="button" class="btn btn-sm" [class.active]="tab() === 'trades'" (click)="switchTab('trades')">
        Trades
      </button>
    </div>

    <section class="card">
      <app-state-block
        [loading]="loading()"
        [error]="error()"
        [hasData]="hasData()"
        [empty]="isEmpty()"
        [emptyTitle]="tab() === 'orders' ? 'No orders yet' : 'No executions yet'"
        emptyHint="Place a market order and it will show up here."
        (retry)="load()"
      >
        @if (tab() === 'orders') {
          @if (orderPage(); as page) {
            <div class="table-scroll">
              <table class="table">
                <thead>
                  <tr>
                    <th>Placed</th>
                    <th>Symbol</th>
                    <th>Side</th>
                    <th class="right">Qty</th>
                    <th class="right">Executed price</th>
                    <th>Status</th>
                    <th>Detail</th>
                  </tr>
                </thead>
                <tbody>
                  @for (order of page.content; track order.id) {
                    <tr>
                      <td class="num small muted">{{ order.createdAt | date: 'dd MMM yyyy, HH:mm:ss' }}</td>
                      <td>
                        <a [routerLink]="['/stocks', order.symbol]"><strong>{{ order.symbol }}</strong></a>
                      </td>
                      <td>
                        <span class="chip" [class.chip-up]="order.side === 'BUY'" [class.chip-down]="order.side === 'SELL'">
                          {{ order.side }}
                        </span>
                      </td>
                      <td class="right num">{{ order.quantity }}</td>
                      <td class="right num">{{ order.executedPrice | money }}</td>
                      <td><app-order-status-chip [status]="order.status" [reason]="order.rejectionReason" /></td>
                      <td class="small muted reason">
                        {{ order.rejectionReason ? rejectionText(order.rejectionReason) : '—' }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        } @else {
          @if (tradePage(); as page) {
            <div class="table-scroll">
              <table class="table">
                <thead>
                  <tr>
                    <th>Executed</th>
                    <th>Symbol</th>
                    <th>Side</th>
                    <th class="right">Qty</th>
                    <th class="right">Price</th>
                    <th class="right">Gross amount</th>
                  </tr>
                </thead>
                <tbody>
                  @for (trade of page.content; track trade.id) {
                    <tr>
                      <td class="num small muted">{{ trade.executedAt | date: 'dd MMM yyyy, HH:mm:ss' }}</td>
                      <td>
                        <a [routerLink]="['/stocks', trade.symbol]"><strong>{{ trade.symbol }}</strong></a>
                      </td>
                      <td>
                        <span class="chip" [class.chip-up]="trade.side === 'BUY'" [class.chip-down]="trade.side === 'SELL'">
                          {{ trade.side }}
                        </span>
                      </td>
                      <td class="right num">{{ trade.quantity }}</td>
                      <td class="right num">{{ trade.price | money }}</td>
                      <td class="right num">{{ trade.grossAmount | money }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        }

        @if (totalPages() > 1) {
          <div class="pager">
            <button type="button" class="btn btn-sm" [disabled]="page() === 0" (click)="goTo(page() - 1)">
              Previous
            </button>
            <span class="small muted">Page {{ page() + 1 }} of {{ totalPages() }} · {{ totalElements() }} records</span>
            <button
              type="button"
              class="btn btn-sm"
              [disabled]="page() >= totalPages() - 1"
              (click)="goTo(page() + 1)"
            >Next</button>
          </div>
        }
      </app-state-block>
    </section>
  `,
  styles: `
    .head { margin-bottom: 18px; flex-wrap: wrap; gap: 14px; }
    .head h1 { margin-bottom: 2px; }
    .head p { margin: 0; max-width: 560px; }
    .tabs { display: flex; gap: 6px; margin-bottom: 14px; }
    .tabs .active { background: var(--accent-dim); border-color: #4c8dff55; color: var(--accent); }
    .reason { max-width: 240px; }
    .pager { display: flex; align-items: center; justify-content: center; gap: 14px; padding-top: 16px; }
  `,
})
export class Orders {
  private readonly api = inject(Api);

  protected readonly tab = signal<Tab>('orders');
  protected readonly page = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<AppError | null>(null);
  protected readonly orderPage = signal<PageResponse<OrderResponse> | null>(null);
  protected readonly tradePage = signal<PageResponse<TradeResponse> | null>(null);

  private readonly current = computed(() =>
    this.tab() === 'orders'
      ? (this.orderPage() as PageResponse<OrderResponse> | null)
      : (this.tradePage() as PageResponse<TradeResponse> | null),
  );

  protected readonly hasData = computed(() => this.current() !== null);
  protected readonly isEmpty = computed(() => (this.current()?.content.length ?? 0) === 0);
  protected readonly totalPages = computed(() => this.current()?.totalPages ?? 0);
  protected readonly totalElements = computed(() => this.current()?.totalElements ?? 0);

  constructor() {
    this.load();
  }

  protected switchTab(tab: Tab): void {
    if (tab === this.tab()) return;
    this.tab.set(tab);
    this.page.set(0);
    this.load();
  }

  protected goTo(page: number): void {
    this.page.set(Math.max(0, page));
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);

    if (this.tab() === 'orders') {
      this.api.orders(this.page()).subscribe({
        next: (page) => {
          this.orderPage.set(page);
          this.loading.set(false);
        },
        error: (appError: AppError) => {
          this.error.set(appError);
          this.loading.set(false);
        },
      });
    } else {
      this.api.trades(this.page()).subscribe({
        next: (page) => {
          this.tradePage.set(page);
          this.loading.set(false);
        },
        error: (appError: AppError) => {
          this.error.set(appError);
          this.loading.set(false);
        },
      });
    }
  }

  protected rejectionText(reason: string): string {
    return REJECTION_TEXT[reason] ?? reason;
  }
}
