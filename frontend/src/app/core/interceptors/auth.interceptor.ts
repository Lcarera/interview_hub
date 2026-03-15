import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

const AUTHENTICATED_PATHS = ['/api/', '/admin/'];

function isBackendRequest(url: string): boolean {
  const isRelative = url.startsWith('/');
  const matchesBackendOrigin =
    environment.apiUrl !== '' && url.startsWith(environment.apiUrl);
  return (
    (isRelative || matchesBackendOrigin) &&
    AUTHENTICATED_PATHS.some((p) => url.includes(p))
  );
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).getToken();
  if (token && isBackendRequest(req.url)) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};
