import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AppError } from '../core/app-error';

/**
 * Every data-driven view routes its loading / error / empty states through this
 * component, so no screen can ship with only a happy path.
 *
 * Content is projected for the ready state; `loading` only takes over the view
 * on the first load, so a background poll refreshing data never blanks the
 * screen the user is reading.
 */
@Component({
  selector: 'app-state-block',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error(); as appError) {
      <div class="state">
        <div class="icon bad">!</div>
        <p class="title">{{ appError.message }}</p>
        <p class="muted small">{{ appError.code }}</p>
        <button type="button" class="btn btn-sm" (click)="retry.emit()">Try again</button>
      </div>
    } @else if (loading() && !hasData()) {
      <div class="state">
        <div class="spinner" aria-label="Loading"></div>
        <p class="muted small">Loading…</p>
      </div>
    } @else if (hasData() && empty()) {
      <div class="state">
        <div class="icon">∅</div>
        <p class="title dim">{{ emptyTitle() }}</p>
        @if (emptyHint()) {
          <p class="muted small">{{ emptyHint() }}</p>
        }
      </div>
    } @else {
      <ng-content />
    }
  `,
  styles: `
    .state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 36px 16px;
      text-align: center;
    }
    .title { margin: 0; font-weight: 500; }
    .muted { margin: 0; }
    .icon {
      width: 34px;
      height: 34px;
      border-radius: 50%;
      display: grid;
      place-items: center;
      background: var(--surface-2);
      color: var(--muted);
      font-weight: 700;
    }
    .icon.bad { background: #f2555a1f; color: var(--down); }
    .spinner {
      width: 22px;
      height: 22px;
      border: 2px solid var(--border-strong);
      border-top-color: var(--accent);
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }
    button { margin-top: 6px; }
  `,
})
export class StateBlock {
  readonly loading = input(false);
  readonly error = input<AppError | null>(null);
  /** True once a response has arrived (so we can tell "no data yet" from "no data"). */
  readonly hasData = input(false);
  readonly empty = input(false);
  readonly emptyTitle = input('Nothing here yet');
  readonly emptyHint = input<string | null>(null);

  readonly retry = output<void>();
}
