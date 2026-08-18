import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { toAppError } from '../app-error';
import { AuthStore } from './auth-store';

/**
 * Converts every HTTP failure into a typed AppError and handles the one global
 * case: a 401 on a non-login request means the session is gone, so clear it and
 * send the user to /login.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const authStore = inject(AuthStore);
  const router = inject(Router);

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      const appError = toAppError(error);
      const isLoginAttempt = request.url.includes('/api/v1/auth/');

      if (appError.status === 401 && !isLoginAttempt) {
        authStore.endSession();
        void router.navigate(['/login'], { queryParams: { reason: 'expired' } });
      }
      return throwError(() => appError);
    }),
  );
};
