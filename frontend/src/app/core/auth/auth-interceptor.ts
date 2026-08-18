import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthStore } from './auth-store';

/** Attaches the bearer token to every API call except the public auth endpoints. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(AuthStore).token();
  const isAuthEndpoint = request.url.includes('/api/v1/auth/');

  if (!token || isAuthEndpoint) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
