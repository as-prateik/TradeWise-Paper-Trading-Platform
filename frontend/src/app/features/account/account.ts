import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Api } from '../../core/api/api';
import { PageResponse, TransactionResponse, UserResponse, WalletResponse } from '../../core/api-types';
import { AppError } from '../../core/app-error';
import { AuthStore } from '../../core/auth/auth-store';
import { MoneyPipe } from '../../core/util/money-pipe';
import { StateBlock } from '../../shared/state-block';

const TYPE_LABEL: Record<string, string> = {
  SEED: 'Opening balance',
  TRADE_DEBIT: 'Buy settlement',
  TRADE_CREDIT: 'Sell proceeds',
};

@Component({
  selector: 'app-account',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MoneyPipe, StateBlock],
  template: `
    <div class="head">
      <h1>Account</h1>
      <p class="muted small">Your profile and every movement of virtual cash.</p>
    </div>

    <div class="top">
      <section class="card">
        <div class="card-head"><h2>Profile</h2></div>
        @if (user(); as profile) {
          <dl class="details">
            <dt class="label">Name</dt>
            <dd>{{ profile.fullName }}</dd>
            <dt class="label">Email</dt>
            <dd>{{ profile.email }}</dd>
            <dt class="label">Member since</dt>
            <dd>{{ profile.createdAt | date: 'dd MMMM yyyy' }}</dd>
            <dt class="label">Account ID</dt>
            <dd class="mono small muted">{{ profile.id }}</dd>
          </dl>
        } @else {
          <p class="muted small">Loading profile…</p>
        }
      </section>

      <section class="card wallet">
        <div class="card-head"><h2>Wallet</h2></div>
        <span class="label">Cash balance</span>
        <span class="figure num">{{ wallet()?.balance | money }}</span>
        <span class="small muted">
          Virtual currency. Updated
          @if (wallet(); as currentWallet) {
            {{ currentWallet.updatedAt | date: 'dd MMM yyyy, HH:mm' }}
          }
        </span>
      </section>
    </div>

    <section class="card">
      <div class="card-head">
        <h2>Cash transactions</h2>
        <span class="small muted">Newest first</span>
      </div>

      <app-state-block
        [loading]="loading()"
        [error]="error()"
        [hasData]="transactions() !== null"
        [empty]="isEmpty()"
        emptyTitle="No transactions"
        (retry)="load()"
      >
        @if (transactions(); as page) {
          <div class="table-scroll">
            <table class="table">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Type</th>
                  <th>Description</th>
                  <th class="right">Amount</th>
                  <th class="right">Balance after</th>
                </tr>
              </thead>
              <tbody>
                @for (transaction of page.content; track transaction.id) {
                  <tr>
                    <td class="num small muted">{{ transaction.createdAt | date: 'dd MMM yyyy, HH:mm:ss' }}</td>
                    <td>
                      <span
                        class="chip"
                        [class.chip-up]="transaction.type === 'TRADE_CREDIT'"
                        [class.chip-down]="transaction.type === 'TRADE_DEBIT'"
                        [class.chip-accent]="transaction.type === 'SEED'"
                      >{{ typeLabel(transaction.type) }}</span>
                    </td>
                    <td class="small dim">{{ transaction.description }}</td>
                    <td
                      class="right num"
                      [class.up]="transaction.type !== 'TRADE_DEBIT'"
                      [class.down]="transaction.type === 'TRADE_DEBIT'"
                    >
                      {{ transaction.type === 'TRADE_DEBIT' ? '-' : '+' }}{{ transaction.amount | money }}
                    </td>
                    <td class="right num">{{ transaction.balanceAfter | money }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          @if (page.totalPages > 1) {
            <div class="pager">
              <button type="button" class="btn btn-sm" [disabled]="page.page === 0" (click)="goTo(page.page - 1)">
                Previous
              </button>
              <span class="small muted">Page {{ page.page + 1 }} of {{ page.totalPages }}</span>
              <button
                type="button"
                class="btn btn-sm"
                [disabled]="page.page >= page.totalPages - 1"
                (click)="goTo(page.page + 1)"
              >Next</button>
            </div>
          }
        }
      </app-state-block>
    </section>
  `,
  styles: `
    .head { margin-bottom: 20px; }
    .head h1 { margin-bottom: 2px; }
    .head p { margin: 0; }
    .top { display: grid; grid-template-columns: 1fr 300px; gap: var(--gap); margin-bottom: var(--gap); align-items: start; }
    .details { display: grid; grid-template-columns: 130px 1fr; gap: 10px 14px; margin: 0; align-items: baseline; }
    .details dd { margin: 0; }
    .mono { font-family: var(--mono); }
    .wallet { display: flex; flex-direction: column; gap: 3px; }
    .figure { font-size: 24px; font-weight: 600; letter-spacing: -0.02em; }
    .pager { display: flex; align-items: center; justify-content: center; gap: 14px; padding-top: 16px; }
    @media (max-width: 860px) { .top { grid-template-columns: 1fr; } }
  `,
})
export class Account {
  private readonly api = inject(Api);
  private readonly authStore = inject(AuthStore);

  protected readonly user = signal<UserResponse | null>(this.authStore.user());
  protected readonly wallet = signal<WalletResponse | null>(null);
  protected readonly transactions = signal<PageResponse<TransactionResponse> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<AppError | null>(null);
  protected readonly page = signal(0);

  protected readonly isEmpty = computed(() => (this.transactions()?.content.length ?? 0) === 0);

  constructor() {
    this.api.currentUser().subscribe({ next: (profile) => this.user.set(profile) });
    this.api.wallet().subscribe({ next: (wallet) => this.wallet.set(wallet) });
    this.load();
  }

  protected goTo(page: number): void {
    this.page.set(Math.max(0, page));
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.transactions(this.page()).subscribe({
      next: (page) => {
        this.transactions.set(page);
        this.loading.set(false);
      },
      error: (appError: AppError) => {
        this.error.set(appError);
        this.loading.set(false);
      },
    });
  }

  protected typeLabel(type: string): string {
    return TYPE_LABEL[type] ?? type;
  }
}
