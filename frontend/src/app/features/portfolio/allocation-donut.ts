import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { HoldingView } from '../../core/api-types';

interface Arc {
  readonly symbol: string;
  readonly percent: number;
  readonly colour: string;
  readonly dashArray: string;
  readonly dashOffset: number;
}

/**
 * Allocation donut, hand-drawn in SVG.
 *
 * A single <circle> per slice with stroke-dasharray/offset avoids pulling in a
 * second charting library just to draw one pie. Colours come from a fixed
 * categorical ramp so the same symbol keeps its colour between renders.
 */
@Component({
  selector: 'app-allocation-donut',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="donut-wrap">
      <svg viewBox="0 0 42 42" class="donut" role="img" [attr.aria-label]="ariaLabel()">
        <circle class="track" cx="21" cy="21" r="15.915" />
        @for (arc of arcs(); track arc.symbol) {
          <circle
            class="slice"
            cx="21"
            cy="21"
            r="15.915"
            [attr.stroke]="arc.colour"
            [attr.stroke-dasharray]="arc.dashArray"
            [attr.stroke-dashoffset]="arc.dashOffset"
          >
            <title>{{ arc.symbol }} — {{ arc.percent.toFixed(2) }}%</title>
          </circle>
        }
        <text x="21" y="20.4" class="count">{{ arcs().length }}</text>
        <text x="21" y="24.4" class="caption">{{ arcs().length === 1 ? 'position' : 'positions' }}</text>
      </svg>

      <ul class="legend">
        @for (arc of arcs(); track arc.symbol) {
          <li>
            <span class="swatch" [style.background]="arc.colour"></span>
            <span class="sym">{{ arc.symbol }}</span>
            <span class="pct num muted">{{ arc.percent.toFixed(1) }}%</span>
          </li>
        }
      </ul>
    </div>
  `,
  styles: `
    .donut-wrap { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; }
    .donut { width: 150px; height: 150px; flex: none; transform: rotate(-90deg); }
    .track { fill: none; stroke: var(--surface-2); stroke-width: 4; }
    .slice { fill: none; stroke-width: 4; transition: stroke-dasharray 0.3s ease; }
    text { transform: rotate(90deg); transform-origin: 21px 21px; text-anchor: middle; fill: var(--text); }
    .count { font-size: 6px; font-weight: 600; }
    .caption { font-size: 2.4px; fill: var(--muted); letter-spacing: 0.08em; text-transform: uppercase; }
    .legend { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; flex: 1; min-width: 150px; }
    .legend li { display: grid; grid-template-columns: 10px 1fr auto; align-items: center; gap: 8px; font-size: 12px; }
    .swatch { width: 10px; height: 10px; border-radius: 3px; }
    .sym { font-weight: 500; }
  `,
})
export class AllocationDonut {
  readonly holdings = input.required<HoldingView[]>();

  private static readonly PALETTE = [
    '#4c8dff',
    '#21c07a',
    '#f5a524',
    '#b57bff',
    '#3ec9d6',
    '#f2555a',
    '#8ab4f8',
    '#e08cc0',
  ];

  protected readonly arcs = computed<Arc[]>(() => {
    let cumulative = 0;
    return this.holdings().map((holding, index) => {
      const percent = holding.allocationPercent;
      // The circle's circumference is normalised to 100 by r = 15.915.
      const arc: Arc = {
        symbol: holding.symbol,
        percent,
        colour: AllocationDonut.PALETTE[index % AllocationDonut.PALETTE.length],
        dashArray: `${percent} ${100 - percent}`,
        dashOffset: -cumulative,
      };
      cumulative += percent;
      return arc;
    });
  });

  protected readonly ariaLabel = computed(
    () =>
      'Portfolio allocation: ' +
      this.arcs()
        .map((arc) => `${arc.symbol} ${arc.percent.toFixed(1)} percent`)
        .join(', '),
  );
}
