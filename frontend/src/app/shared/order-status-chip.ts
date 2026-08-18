import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OrderStatus } from '../core/api-types';

/** Human wording for the backend's machine-readable rejection reasons. */
export const REJECTION_TEXT: Record<string, string> = {
  INSUFFICIENT_FUNDS: 'Not enough cash to cover this order',
  INSUFFICIENT_SHARES: 'You do not hold enough shares to sell',
  MARKET_CLOSED: 'The market was closed when the order was placed',
};

@Component({
  selector: 'app-order-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="chip" [class]="chipClass()" [title]="tooltip()">
      {{ status() }}
    </span>
  `,
  styles: `:host { display: inline-flex; }`,
})
export class OrderStatusChip {
  readonly status = input.required<OrderStatus>();
  readonly reason = input<string | null>(null);

  readonly chipClass = computed(() => {
    switch (this.status()) {
      case 'EXECUTED':
        return 'chip-up';
      case 'REJECTED':
        return 'chip-down';
      case 'PENDING':
        return 'chip-warn';
      default:
        return 'chip-neutral';
    }
  });

  readonly tooltip = computed(() => {
    const reason = this.reason();
    if (!reason) return this.status();
    return REJECTION_TEXT[reason] ?? reason;
  });
}
