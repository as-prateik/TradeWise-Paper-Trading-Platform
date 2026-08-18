import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MarketStatus } from '../core/api-types';

@Component({
  selector: 'app-market-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [class]="chipClass()">{{ label() }}</span>`,
  styles: `:host { display: inline-flex; }`,
})
export class MarketStatusChip {
  readonly status = input.required<MarketStatus>();

  readonly label = computed(() => {
    switch (this.status()) {
      case 'OPEN':
        return 'MARKET OPEN';
      case 'PRE_OPEN':
        return 'PRE-OPEN';
      case 'HOLIDAY':
        return 'HOLIDAY';
      default:
        return 'MARKET CLOSED';
    }
  });

  readonly chipClass = computed(() => {
    switch (this.status()) {
      case 'OPEN':
        return 'chip-up';
      case 'PRE_OPEN':
        return 'chip-warn';
      default:
        return 'chip-neutral';
    }
  });
}
