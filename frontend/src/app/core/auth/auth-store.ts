import { computed, Injectable, signal } from '@angular/core';
import { AuthResponse, UserResponse } from '../api-types';

const TOKEN_KEY = 'tradewise.token';
const USER_KEY = 'tradewise.user';

/**
 * Holds the session. The token lives in localStorage so a refresh doesn't log
 * the user out; there is no refresh token in Phase 1, so an expired token
 * simply means re-login (the error interceptor handles the 401).
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly tokenSignal = signal<string | null>(readString(TOKEN_KEY));
  private readonly userSignal = signal<UserResponse | null>(readJson<UserResponse>(USER_KEY));

  readonly token = this.tokenSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null);

  startSession(auth: AuthResponse): void {
    this.tokenSignal.set(auth.accessToken);
    this.userSignal.set(auth.user);
    localStorage.setItem(TOKEN_KEY, auth.accessToken);
    localStorage.setItem(USER_KEY, JSON.stringify(auth.user));
  }

  endSession(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }
}

function readString(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function readJson<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}
