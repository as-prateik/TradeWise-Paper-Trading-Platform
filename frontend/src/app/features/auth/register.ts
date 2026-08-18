import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api/api';
import { AppError, fieldError } from '../../core/app-error';
import { AuthStore } from '../../core/auth/auth-store';

@Component({
  selector: 'app-register',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-card card">
      <div class="head">
        <span class="mark">TW</span>
        <h1>Create your account</h1>
        <p class="muted small">You start with $100,000 of virtual cash to trade with.</p>
      </div>

      @if (error(); as appError) {
        @if (appError.fieldErrors.length === 0) {
          <div class="banner banner-bad">{{ appError.message }}</div>
        }
      }

      <form class="stack" (ngSubmit)="submit()" #form="ngForm">
        <div class="field">
          <label for="fullName">Full name</label>
          <input
            id="fullName"
            name="fullName"
            class="input"
            autocomplete="name"
            required
            [(ngModel)]="fullName"
            [class.invalid]="nameError()"
          />
          @if (nameError(); as message) {
            <span class="field-error">{{ message }}</span>
          }
        </div>

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
            autocomplete="new-password"
            required
            [(ngModel)]="password"
            [class.invalid]="passwordError()"
          />
          <span class="muted small">At least 8 characters, with one letter and one digit.</span>
          @if (passwordError(); as message) {
            <span class="field-error">{{ message }}</span>
          }
        </div>

        <button type="submit" class="btn btn-primary" [disabled]="submitting() || form.invalid">
          {{ submitting() ? 'Creating account…' : 'Create account' }}
        </button>
      </form>

      <p class="small muted center">Already registered? <a routerLink="/login">Sign in</a></p>
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
export class Register {
  private readonly api = inject(Api);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);

  protected fullName = '';
  protected email = '';
  protected password = '';
  protected readonly submitting = signal(false);
  protected readonly error = signal<AppError | null>(null);

  protected nameError = () => fieldError(this.error(), 'fullName');
  protected emailError = () => fieldError(this.error(), 'email');
  protected passwordError = () => fieldError(this.error(), 'password');

  protected submit(): void {
    if (this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);

    this.api
      .register({ fullName: this.fullName, email: this.email, password: this.password })
      .subscribe({
        next: (auth) => {
          // Registration auto-logs-in: the response already carries a token.
          this.authStore.startSession(auth);
          void this.router.navigateByUrl('/dashboard');
        },
        error: (appError: AppError) => {
          this.error.set(appError);
          this.submitting.set(false);
        },
      });
  }
}
