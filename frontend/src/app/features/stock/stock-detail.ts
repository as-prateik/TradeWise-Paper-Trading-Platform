import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  CandlestickData,
  CandlestickSeries,
  createChart,
  IChartApi,
  ISeriesApi,
  Time,
  UTCTimestamp,
} from 'lightweight-charts';
import { Api } from '../../core/api/api';
import { Quote, TimeRange } from '../../core/api-types';
import { AppError } from '../../core/app-error';
import { MoneyPipe, Percent2Pipe } from '../../core/util/money-pipe';
import { POLL_INTERVAL_MS, pollWhileVisible } from '../../core/util/polling';
import { StateBlock } from '../../shared/state-block';

interface RangeOption {
  readonly value: TimeRange;
  readonly label: string;
}

@Component({
  selector: 'app-stock-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MoneyPipe, Percent2Pipe, StateBlock],
  template: `
    <div class="spread head">
      <div>
        <div class="row">
          <h1>{{ symbol() }}</h1>
          @if (quote(); as liveQuote) {
            <span class="chip chip-neutral">{{ liveQuote.companyName }}</span>
          }
        </div>
        <p class="muted small"><a routerLink="/trade">← Back to trading</a></p>
      </div>
      <a class="btn btn-primary" routerLink="/trade">Trade this stock</a>
    </div>

    <app-state-block
      [loading]="loading()"
      [error]="error()"
      [hasData]="quote() !== null"
      (retry)="reload()"
    >
      @if (quote(); as liveQuote) {
        <div class="card quote-card">
          <div class="spread wrap">
            <div>
              <div class="price num">{{ liveQuote.price | money }}</div>
              <div
                class="num"
                [class.up]="liveQuote.changeAbsolute > 0"
                [class.down]="liveQuote.changeAbsolute < 0"
              >
                {{ liveQuote.changeAbsolute | money: 'signed' }} ({{ liveQuote.changePercent | percent2: true }}) today
              </div>
            </div>
            <div class="stats">
              <div><span class="label">Open</span><span class="num">{{ liveQuote.dayOpen | money: 'plain' }}</span></div>
              <div><span class="label">High</span><span class="num">{{ liveQuote.dayHigh | money: 'plain' }}</span></div>
              <div><span class="label">Low</span><span class="num">{{ liveQuote.dayLow | money: 'plain' }}</span></div>
              <div><span class="label">Prev close</span><span class="num">{{ liveQuote.previousClose | money: 'plain' }}</span></div>
              <div><span class="label">Volume</span><span class="num">{{ liveQuote.volume.toLocaleString('en-IN') }}</span></div>
            </div>
          </div>
        </div>
      }
    </app-state-block>

    <!--
      The chart card sits OUTSIDE app-state-block on purpose: projected content
      inside a conditional is not attached to the DOM while loading, and a
      lightweight-charts instance created against a detached element initialises
      at zero size and never paints. Keeping the host permanently attached means
      the chart is always measurable; this card renders its own states instead.
    -->
    <div class="card chart-card">
      <div class="card-head">
        <h2>Price history</h2>
        <div class="ranges">
          @for (option of ranges; track option.value) {
            <button
              type="button"
              class="btn btn-sm"
              [class.active]="option.value === range()"
              (click)="setRange(option.value)"
            >{{ option.label }}</button>
          }
        </div>
      </div>

      @if (historyError(); as appError) {
        <div class="banner banner-bad">{{ appError.message }}</div>
      } @else if (historyLoading()) {
        <p class="muted small loading-note">Loading candles…</p>
      }
      <div #chartHost class="chart" [class.hidden]="historyError() !== null"></div>
    </div>
  `,
  styles: `
    .head { margin-bottom: 20px; flex-wrap: wrap; }
    .head p { margin: 2px 0 0; }
    .quote-card { margin-bottom: var(--gap); }
    .price { font-size: 30px; font-weight: 600; letter-spacing: -0.02em; }
    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(88px, 1fr)); gap: 14px; min-width: 320px; }
    .stats > div { display: flex; flex-direction: column; }
    .ranges { display: flex; gap: 4px; flex-wrap: wrap; }
    .ranges .active { background: var(--accent-dim); border-color: #4c8dff55; color: var(--accent); }
    .chart-card { margin-top: var(--gap); }
    .chart { width: 100%; height: 380px; }
    .chart.hidden { display: none; }
    .loading-note { margin: 0 0 8px; }
  `,
})
export class StockDetail implements OnInit, AfterViewInit {
  /** Bound from the :symbol route parameter via withComponentInputBinding(). */
  readonly symbol = input.required<string>();

  private readonly api = inject(Api);
  private readonly destroyRef = inject(DestroyRef);
  private readonly chartHost = viewChild.required<ElementRef<HTMLDivElement>>('chartHost');

  protected readonly ranges: RangeOption[] = [
    { value: 'ONE_DAY', label: '1D' },
    { value: 'ONE_WEEK', label: '1W' },
    { value: 'ONE_MONTH', label: '1M' },
    { value: 'THREE_MONTHS', label: '3M' },
    { value: 'ONE_YEAR', label: '1Y' },
  ];

  protected readonly quote = signal<Quote | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<AppError | null>(null);
  protected readonly range = signal<TimeRange>('ONE_MONTH');
  protected readonly historyLoading = signal(true);
  protected readonly historyError = signal<AppError | null>(null);

  private chart: IChartApi | null = null;
  private series: ISeriesApi<'Candlestick'> | null = null;

  /**
   * Polling starts in ngOnInit, not the constructor: `symbol` is a required
   * input and required inputs are not readable until after construction
   * (reading one early throws NG0950).
   */
  ngOnInit(): void {
    pollWhileVisible(() => this.api.quote(this.symbol()), POLL_INTERVAL_MS, this.destroyRef).subscribe({
      next: (quote) => {
        this.quote.set(quote);
        this.loading.set(false);
        this.error.set(null);
      },
      error: (appError: AppError) => {
        this.error.set(appError);
        this.loading.set(false);
      },
    });
  }

  ngAfterViewInit(): void {
    this.chart = createChart(this.chartHost().nativeElement, {
      layout: {
        background: { color: 'transparent' },
        textColor: '#a9b4c4',
        fontFamily: getComputedStyle(document.body).fontFamily,
      },
      grid: {
        vertLines: { color: '#1c2430' },
        horzLines: { color: '#1c2430' },
      },
      rightPriceScale: { borderColor: '#232c3a' },
      timeScale: { borderColor: '#232c3a', timeVisible: true, secondsVisible: false },
      autoSize: true,
    });

    this.series = this.chart.addSeries(CandlestickSeries, {
      upColor: '#21c07a',
      downColor: '#f2555a',
      borderUpColor: '#21c07a',
      borderDownColor: '#f2555a',
      wickUpColor: '#21c07a',
      wickDownColor: '#f2555a',
    });

    this.destroyRef.onDestroy(() => {
      this.chart?.remove();
      this.chart = null;
      this.series = null;
    });

    this.loadHistory();
  }

  protected setRange(range: TimeRange): void {
    if (range === this.range()) return;
    this.range.set(range);
    this.loadHistory();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.quote(this.symbol()).subscribe({
      next: (quote) => {
        this.quote.set(quote);
        this.loading.set(false);
      },
      error: (appError: AppError) => {
        this.error.set(appError);
        this.loading.set(false);
      },
    });
    this.loadHistory();
  }

  private loadHistory(): void {
    this.historyLoading.set(true);
    this.historyError.set(null);

    this.api.history(this.symbol(), this.range()).subscribe({
      next: (history) => {
        const candles: CandlestickData<Time>[] = history.candles.map((candle) => ({
          time: (Date.parse(candle.timestamp) / 1000) as UTCTimestamp,
          open: candle.open,
          high: candle.high,
          low: candle.low,
          close: candle.close,
        }));
        this.series?.setData(candles);
        this.chart?.timeScale().fitContent();
        this.historyLoading.set(false);
      },
      error: (appError: AppError) => {
        this.historyError.set(appError);
        this.historyLoading.set(false);
      },
    });
  }
}
