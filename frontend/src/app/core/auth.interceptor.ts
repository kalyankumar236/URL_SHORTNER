import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * Attaches the admin Basic auth header to API requests. Public read endpoints ignore it;
 * protected endpoints require it. Only decorates calls to our own API base.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const header = auth.authHeader();
  if (header && req.url.includes('/api/')) {
    req = req.clone({ setHeaders: { Authorization: header } });
  }
  return next(req);
};
