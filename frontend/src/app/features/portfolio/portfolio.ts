import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api/api';
import { PortfolioResponse } from '../../core/api-types';
import { AppError } from '../../core/app-error';
import { MoneyPipe, Percent2Pipe } from '../../core/util/money-pipe';
import { pollWhileVisible } from '../../core/util/polling';
import { StateBlock } from '../../shared/state-block';
import { AllocationDonut } from './allocation-donut';

@Component({
  selector: 'app-portfolio',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MoneyPipe, Percent2Pipe, StateBlock, AllocationDonut],
  template: `
    <div class="spread head">
      <div>
        <h1>Portfolio</h1>
        <p class="muted small">Positions carried at weighted-average cost.</p>
      </div>
      <a class="btn btn-primary" routerLink="/trade">Place a trade</a>
    </div>

    <app-state-block
      [loading]="loading()"
      [error]="error()"
      [hasData]="data() !== null"
      [empty]="isEmpty()"
      emptyTitle="No open positions"
      emptyHint="Buy a stock and it will appear here with live valuation and P&L."
      (retry)="load()"
    >
      @if (data(); as portfolio) {
        <div class="tiles">
          <div class="card tile">
            <span class="label">Invested</span>
            <span class="figure num">{{ portfolio.totals.investedValue | money }}</span>
          </div>
          <div class="card tile">
            <span class="label">Market value</span>
            <span class="figure num">{{ portfolio.totals.marketValue | money }}</span>
          </div>
          <div class="card tile">
            <span class="label">Unrealised P&amp;L</span>
            <span
              class="figure num"
              [class.up]="portfolio.totals.unrealizedPnl > 0"
              [class.down]="portfolio.totals.unrealizedPnl < 0"
            >{{ portfolio.totals.unrealizedPnl | money: 'signed' }}</span>
            @if (portfolio.totals.investedValue > 0) {
              <span class="small muted">
                {{ (portfolio.totals.unrealizedPnl / portfolio.totals.investedValue) * 100 | percent2: true }} on cost
              </span>
            }
          </div>
          <div class="card tile">
            <span class="label">Realised P&amp;L</span>
            <span
              class="figure num"
              [class.up]="portfolio.totals.realizedPnl > 0"
              [class.down]="portfolio.totals.realizedPnl < 0"
            >{{ portfolio.totals.realizedPnl | money: 'signed' }}</span>
            <span class="small muted">Includes fully closed positions</span>
          </div>
        </div>

        <div class="lower">
          <section class="card">
            <div class="card-head"><h2>Holdings</h2></div>
            <div class="table-scroll">
              <table class="table">
                <thead>
                  <tr>
                    <th>Symbol</th>
                    <th class="right">Qty</th>
                    <th class="right">Avg cost</th>
                    <th class="right">LTP</th>
                    <th class="right">Invested</th>
                    <th class="right">Market value</th>
                    <th class="right">Unrealised P&amp;L</th>
                    <th class="right">Weight</th>
                  </tr>
                </thead>
                <tbody>
                  @for (holding of portfolio.holdings; track holding.symbol) {
                    <tr class="clickable" [routerLink]="['/stocks', holding.symbol]">
                      <td>
                        <div class="sym">{{ holding.symbol }}</div>
                        <div class="small muted co">{{ holding.companyName }}</div>
                      </td>
                      <td class="right num">{{ holding.quantity }}</td>
                      <td class="right num">{{ holding.averagePrice | money: 'plain' }}</td>
                      <td class="right num">{{ holding.currentPrice | money: 'plain' }}</td>
                      <td class="right num">{{ holding.investedValue | money }}</td>
                      <td class="right num">{{ holding.marketValue | money }}</td>
                      <td
                        class="right num"
                        [class.up]="holding.unrealizedPnl > 0"
                        [class.down]="holding.unrealizedPnl < 0"
                      >
                        {{ holding.unrealizedPnl | money: 'signed' }}
                        <div class="small">{{ holding.unrealizedPnlPercent | percent2: true }}</div>
                      </td>
                      <td class="right num muted">{{ holding.allocationPercent | percent2 }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>

          <section class="card">
            <div class="card-head"><h2>Allocation</h2></div>
            <app-allocation-donut [holdings]="portfolio.holdings" />
          </section>
        </div>
      }
    </app-state-block>
  `,
  styles: `
    .head { margin-bottom: 20px; flex-wrap: wrap; }
    .head h1 { margin-bottom: 2px; }
    .head p { margin: 0; }
    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: var(--gap); margin-bottom: var(--gap); }
    .tile { display: flex; flex-direction: column; gap: 4px; }
    .figure { font-size: 22px; font-weight: 600; letter-spacing: -0.02em; }
    .lower { display: grid; grid-template-columns: 1fr 330px; gap: var(--gap); align-items: start; }
    .sym { font-weight: 600; }
    .co { max-width: 190px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    @media (max-width: 1000px) { .lower { grid-template-columns: 1fr; } }
  `,
})
export class Portfolio {
  private readonly api = inject(Api);

  protected readonly data = signal<PortfolioResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<AppError | null>(null);

  protected readonly isEmpty = computed(() => (this.data()?.holdings.length ?? 0) === 0);

  constructor() {
    pollWhileVisible(() => this.api.portfolio()).subscribe({
      next: (portfolio) => {
        this.data.set(portfolio);
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
    this.api.portfolio().subscribe({
      next: (portfolio) => {
        this.data.set(portfolio);
        this.loading.set(false);
      },
      error: (appError: AppError) => {
        this.error.set(appError);
        this.loading.set(false);
      },
    });
  }
}
