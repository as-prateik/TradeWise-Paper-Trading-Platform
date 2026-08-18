import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api/api';
import { AppError, fieldError } from '../../core/app-error';
import { AuthStore } from '../../core/auth/auth-store';

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-card card">
      <div class="head">
        <span class="mark">TW</span>
        <h1>Sign in to TradeWise</h1>
        <p class="muted small">Paper trading. Real market mechanics, virtual money.</p>
      </div>

      @if (sessionExpired()) {
        <div class="banner banner-info">Your session expired — please sign in again.</div>
      }

      @if (error(); as appError) {
        @if (appError.fieldErrors.length === 0) {
          <div class="banner banner-bad">{{ appError.message }}</div>
        }
      }

      <form class="stack" (ngSubmit)="submit()" #form="ngForm">
        <div class="field">
          <label for="email">Email</label>
          <input
            id="email"
            name="email"
            type="email"
            class="input"
            autocomplete="email"
            required
            [(ngModel)]="email"
            [class.invalid]="emailError()"
          />
          @if (emailError(); as message) {
            <span class="field-error">{{ message }}</span>
          }
        </div>

        <div class="field">
          <label for="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            class="input"
            autocomplete="current-password"
            required
            [(ngModel)]="password"
            [class.invalid]="passwordError()"
          />
          @if (passwordError(); as message) {
            <span class="field-error">{{ message }}</span>
          }
        </div>

        <button type="submit" class="btn btn-primary" [disabled]="submitting() || form.invalid">
          {{ submitting() ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>

      <p class="small muted center">
        No account yet? <a routerLink="/register">Create one</a> — it comes with ₹10,00,000 in virtual cash.
      </p>
    </div>
  `,
  styles: `
    .auth-card { width: min(400px, 100%); display: flex; flex-direction: column; gap: 18px; }
    .head { display: flex; flex-direction: column; align-items: center; gap: 6px; text-align: center; }
    .head h1 { font-size: 19px; }
    .head p { margin: 0; }
    .mark {
      display: grid; place-items: center;
      width: 40px; height: 40px; border-radius: 10px;
      background: var(--accent); color: #04122c; font-weight: 700; margin-bottom: 4px;
    }
    p.center { margin: 0; }
  `,
})
export class Login {
  private readonly api = inject(Api);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected email = '';
  protected password = '';
  protected readonly submitting = signal(false);
  protected readonly error = signal<AppError | null>(null);
  protected readonly sessionExpired = signal(
    this.route.snapshot.queryParamMap.get('reason') === 'expired',
  );

  protected emailError = () => fieldError(this.error(), 'email');
  protected passwordError = () => fieldError(this.error(), 'password');

  protected submit(): void {
    if (this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);
    this.sessionExpired.set(false);

    this.api.login({ email: this.email, password: this.password }).subscribe({
      next: (auth) => {
        this.authStore.startSession(auth);
        const redirectTo = this.route.snapshot.queryParamMap.get('redirectTo');
        void this.router.navigateByUrl(redirectTo ?? '/dashboard');
      },
      error: (appError: AppError) => {
        this.error.set(appError);
        this.submitting.set(false);
      },
    });
  }
}
