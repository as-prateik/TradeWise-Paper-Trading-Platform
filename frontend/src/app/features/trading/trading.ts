import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Api } from '../../core/api/api';
import { OrderResponse, OrderSide, Quote, StockSearchResult, WalletResponse } from '../../core/api-types';
import { AppError } from '../../core/app-error';
import { newIdempotencyKey } from '../../core/util/idempotency';
import { MoneyPipe, Percent2Pipe } from '../../core/util/money-pipe';
import { pollWhileVisible } from '../../core/util/polling';
import { DataSourceChip } from '../../shared/data-source-chip';
import { REJECTION_TEXT } from '../../shared/order-status-chip';

@Component({
  selector: 'app-trading',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, MoneyPipe, Percent2Pipe, DataSourceChip],
  template: `
    <div class="spread head">
      <div>
        <h1>Trade</h1>
        <p class="muted small">Market orders execute immediately at the price shown.</p>
      </div>
      <div class="cash card card-tight">
        <span class="label">Cash available</span>
        <span class="num cash-figure">{{ wallet()?.balance | money }}</span>
      </div>
    </div>

    <div class="layout">
      <section class="card">
        <div class="card-head"><h2>Find a stock</h2></div>

        <input
          class="input"
          type="search"
          placeholder="Search by symbol or company — try “apple” or “NVDA”"
          [ngModel]="searchTerm()"
          (ngModelChange)="onSearchInput($event)"
          aria-label="Search stocks"
        />

        @if (searching()) {
          <p class="muted small pad">Searching…</p>
        } @else if (searchTerm().length > 0 && results().length === 0) {
          <p class="muted small pad">No stocks match “{{ searchTerm() }}”.</p>
        } @else {
          <ul class="results">
            @for (result of results(); track result.symbol) {
              <li>
                <button
                  type="button"
                  class="result"
                  [class.selected]="result.symbol === selectedSymbol()"
                  (click)="select(result.symbol)"
                >
                  <span class="sym">{{ result.symbol }}</span>
                  <span class="co small muted">{{ result.companyName }}</span>
                  <span class="chip chip-neutral">{{ result.exchange }}</span>
                </button>
              </li>
            }
          </ul>
        }
      </section>

      <section class="card">
        @if (!selectedSymbol()) {
          <div class="placeholder">
            <p class="dim">Pick a stock to see its quote and place an order.</p>
          </div>
        } @else if (quoteError(); as appError) {
          <div class="banner banner-bad">{{ appError.message }}</div>
        } @else if (quote(); as liveQuote) {
          <div class="quote-head">
            <div>
              <div class="row">
                <h2>{{ liveQuote.symbol }}</h2>
                <a class="small" [routerLink]="['/stocks', liveQuote.symbol]">Chart &amp; details →</a>
              </div>
              <p class="muted small">{{ liveQuote.companyName }}</p>
            </div>
            <div class="right">
              <app-data-source-chip [source]="liveQuote.source" [tradingDay]="liveQuote.tradingDay" />
              <div class="price num">{{ liveQuote.price | money }}</div>
              <div
                class="num small"
                [class.up]="liveQuote.changeAbsolute > 0"
                [class.down]="liveQuote.changeAbsolute < 0"
              >
                {{ liveQuote.changeAbsolute | money: 'signed' }} ({{ liveQuote.changePercent | percent2: true }})
              </div>
            </div>
          </div>

          <div class="stats">
            <div><span class="label">Open</span><span class="num">{{ liveQuote.dayOpen | money: 'plain' }}</span></div>
            <div><span class="label">High</span><span class="num">{{ liveQuote.dayHigh | money: 'plain' }}</span></div>
            <div><span class="label">Low</span><span class="num">{{ liveQuote.dayLow | money: 'plain' }}</span></div>
            <div><span class="label">Prev close</span><span class="num">{{ liveQuote.previousClose | money: 'plain' }}</span></div>
            <div><span class="label">Volume</span><span class="num">{{ liveQuote.volume.toLocaleString('en-US') }}</span></div>
          </div>

          <hr class="divider" />

          <div class="ticket">
            <div class="sides">
              <button
                type="button"
                class="btn"
                [class.btn-buy]="side() === 'BUY'"
                (click)="side.set('BUY')"
              >Buy</button>
              <button
                type="button"
                class="btn"
                [class.btn-sell]="side() === 'SELL'"
                (click)="side.set('SELL')"
              >Sell</button>
            </div>

            <div class="field">
              <label for="qty">Quantity</label>
              <input
                id="qty"
                class="input"
                type="number"
                min="1"
                step="1"
                [ngModel]="quantity()"
                (ngModelChange)="quantity.set($event)"
              />
            </div>

            <div class="preview">
              <div class="spread small">
                <span class="muted">Order type</span>
                <span>MARKET</span>
              </div>
              <div class="spread small">
                <span class="muted">Estimated {{ side() === 'BUY' ? 'cost' : 'proceeds' }}</span>
                <span class="num">{{ estimatedValue() | money }}</span>
              </div>
              @if (side() === 'BUY' && wallet(); as currentWallet) {
                <div class="spread small">
                  <span class="muted">Cash after</span>
                  <span class="num" [class.down]="estimatedValue() > currentWallet.balance">
                    {{ currentWallet.balance - estimatedValue() | money }}
                  </span>
                </div>
              }
              <p class="muted xsmall">
                Estimate only — the server prices the order at execution and is the authority on the final amount.
              </p>
            </div>

            <button
              type="button"
              class="btn submit"
              [class.btn-buy]="side() === 'BUY'"
              [class.btn-sell]="side() === 'SELL'"
              [disabled]="!canSubmit()"
              (click)="submit()"
            >
              {{ submitting() ? 'Placing…' : (side() === 'BUY' ? 'Buy' : 'Sell') + ' ' + quantity() + ' ' + selectedSymbol() }}
            </button>

            @if (outcome(); as order) {
              @if (order.status === 'EXECUTED') {
                <div class="banner banner-ok">
                  <strong>Filled.</strong>
                  {{ order.side }} {{ order.quantity }} {{ order.symbol }} at {{ order.executedPrice | money }}.
                  <a routerLink="/portfolio">View portfolio</a>
                </div>
              } @else {
                <div class="banner banner-bad">
                  <strong>Order rejected.</strong>
                  {{ rejectionText(order.rejectionReason) }}
                </div>
              }
            }

            @if (submitError(); as appError) {
              <div class="banner banner-bad">{{ appError.message }}</div>
            }
          </div>
        } @else {
          <p class="muted small pad">Loading quote…</p>
        }
      </section>
    </div>
  `,
  styles: `
    .head { margin-bottom: 20px; flex-wrap: wrap; }
    .head h1 { margin-bottom: 2px; }
    .head p { margin: 0; }
    .cash { display: flex; flex-direction: column; gap: 2px; min-width: 190px; }
    .cash-figure { font-size: 19px; font-weight: 600; }

    .layout { display: grid; grid-template-columns: 1fr 1fr; gap: var(--gap); align-items: start; }

    .results { list-style: none; margin: 12px 0 0; padding: 0; display: flex; flex-direction: column; gap: 4px; max-height: 460px; overflow-y: auto; }
    .result {
      width: 100%; display: grid; grid-template-columns: 96px 1fr auto; align-items: center; gap: 10px;
      background: transparent; border: 1px solid transparent; border-radius: var(--radius-sm);
      padding: 9px 10px; cursor: pointer; text-align: left; color: var(--text); font: inherit;
    }
    .result:hover { background: var(--surface-2); }
    .result.selected { background: var(--accent-dim); border-color: #4c8dff55; }
    .sym { font-weight: 600; }
    .co { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .pad { padding: 14px 2px 0; }

    .quote-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
    .quote-head p { margin: 2px 0 0; }
    .price { font-size: 26px; font-weight: 600; letter-spacing: -0.02em; }

    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(84px, 1fr)); gap: 10px; margin-top: 16px; }
    .stats > div { display: flex; flex-direction: column; gap: 1px; }

    .ticket { display: flex; flex-direction: column; gap: 14px; }
    .sides { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
    .preview { background: var(--bg); border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 12px; display: flex; flex-direction: column; gap: 6px; }
    .xsmall { font-size: 11px; margin: 4px 0 0; line-height: 1.4; }
    .submit { padding: 12px; font-size: 15px; }
    .placeholder { display: grid; place-items: center; min-height: 260px; text-align: center; }

    @media (max-width: 900px) {
      .layout { grid-template-columns: 1fr; }
    }
  `,
})
export class Trading {
  private readonly api = inject(Api);
  private readonly searchInput = new Subject<string>();

  protected readonly searchTerm = signal('');
  protected readonly results = signal<StockSearchResult[]>([]);
  protected readonly searching = signal(false);

  protected readonly selectedSymbol = signal<string | null>(null);
  protected readonly quote = signal<Quote | null>(null);
  protected readonly quoteError = signal<AppError | null>(null);

  protected readonly wallet = signal<WalletResponse | null>(null);

  protected readonly side = signal<OrderSide>('BUY');
  protected readonly quantity = signal(1);
  protected readonly submitting = signal(false);
  protected readonly outcome = signal<OrderResponse | null>(null);
  protected readonly submitError = signal<AppError | null>(null);

  /** Client-side preview only; the backend prices the actual execution. */
  protected readonly estimatedValue = computed(() => {
    const price = this.quote()?.price ?? 0;
    return price * Math.max(0, this.quantity());
  });

  protected readonly canSubmit = computed(
    () => !this.submitting() && this.quote() !== null && this.quantity() >= 1,
  );

  constructor() {
    this.searchInput
      .pipe(debounceTime(220), distinctUntilChanged(), switchMap((term) => this.api.search(term)), takeUntilDestroyed())
      .subscribe({
        next: (results) => {
          this.results.set(results);
          this.searching.set(false);
        },
        error: () => this.searching.set(false),
      });

    // Keep the quote and the cash balance fresh while the ticket is open.
    pollWhileVisible(() => this.api.wallet()).subscribe({
      next: (wallet) => this.wallet.set(wallet),
      error: () => undefined,
    });

    // Seed the list so the screen isn't empty on arrival.
    this.onSearchInput('a');
  }

  protected onSearchInput(term: string): void {
    this.searchTerm.set(term);
    if (term.trim().length === 0) {
      this.results.set([]);
      this.searching.set(false);
      return;
    }
    this.searching.set(true);
    this.searchInput.next(term.trim());
  }

  protected select(symbol: string): void {
    this.selectedSymbol.set(symbol);
    this.quote.set(null);
    this.quoteError.set(null);
    this.outcome.set(null);
    this.submitError.set(null);
    this.refreshQuote();
  }

  private refreshQuote(): void {
    const symbol = this.selectedSymbol();
    if (!symbol) return;
    this.api.quote(symbol).subscribe({
      next: (quote) => this.quote.set(quote),
      error: (appError: AppError) => this.quoteError.set(appError),
    });
  }

  protected submit(): void {
    const symbol = this.selectedSymbol();
    if (!symbol || !this.canSubmit()) return;

    this.submitting.set(true);
    this.outcome.set(null);
    this.submitError.set(null);

    // One key per submission: a double-click or retry returns the original
    // order instead of opening a second position.
    const idempotencyKey = newIdempotencyKey();

    this.api
      .placeOrder({ symbol, side: this.side(), type: 'MARKET', quantity: this.quantity() }, idempotencyKey)
      .subscribe({
        next: (order) => {
          // EXECUTED and REJECTED are both normal outcomes, shown in-place.
          this.outcome.set(order);
          this.submitting.set(false);
          this.refreshQuote();
          this.api.wallet().subscribe({ next: (wallet) => this.wallet.set(wallet) });
        },
        error: (appError: AppError) => {
          this.submitError.set(appError);
          this.submitting.set(false);
        },
      });
  }

  protected rejectionText(reason: string | null): string {
    if (!reason) return 'The order could not be executed.';
    return REJECTION_TEXT[reason] ?? reason;
  }
}
