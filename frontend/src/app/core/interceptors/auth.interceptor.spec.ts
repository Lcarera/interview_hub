import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('should add Authorization header for /api/ endpoints', () => {
    const payload = btoa(JSON.stringify({ sub: 'p-1' }));
    authService.handleCallback(`h.${payload}.s`, 'test@gm2dev.com', 3600);

    http.get('/api/interviews').subscribe();

    const req = httpTesting.expectOne('/api/interviews');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer h.${payload}.s`);
    req.flush([]);
  });

  it('should add Authorization header for /admin/ endpoints', () => {
    const payload = btoa(JSON.stringify({ sub: 'p-1' }));
    authService.handleCallback(`h.${payload}.s`, 'test@gm2dev.com', 3600);

    http.get('/admin/users').subscribe();

    const req = httpTesting.expectOne('/admin/users');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer h.${payload}.s`);
    req.flush([]);
  });

  it('should NOT add Authorization header for /auth/ endpoints', () => {
    const payload = btoa(JSON.stringify({ sub: 'p-1' }));
    authService.handleCallback(`h.${payload}.s`, 'test@gm2dev.com', 3600);

    http.post('/auth/register', { email: 'a@b.com' }).subscribe();

    const req = httpTesting.expectOne('/auth/register');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should NOT add Authorization header for third-party URLs', () => {
    const payload = btoa(JSON.stringify({ sub: 'p-1' }));
    authService.handleCallback(`h.${payload}.s`, 'test@gm2dev.com', 3600);

    http.get('https://third-party.example.com/data').subscribe();

    const req = httpTesting.expectOne('https://third-party.example.com/data');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should NOT add Authorization header for third-party URLs with /api/ path', () => {
    const payload = btoa(JSON.stringify({ sub: 'p-1' }));
    authService.handleCallback(`h.${payload}.s`, 'test@gm2dev.com', 3600);

    http.get('https://evil.com/api/data').subscribe();

    const req = httpTesting.expectOne('https://evil.com/api/data');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should not add Authorization header when no token exists', () => {
    http.get('/api/interviews').subscribe();

    const req = httpTesting.expectOne('/api/interviews');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });
});
