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

  it('should add Authorization header for non-auth endpoints', () => {
    const payload = btoa(JSON.stringify({ sub: 'p-1' }));
    authService.handleCallback(`h.${payload}.s`, 'test@gm2dev.com', 3600);

    http.get('/api/interviews').subscribe();

    const req = httpTesting.expectOne('/api/interviews');
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

  it('should not add Authorization header when no token exists', () => {
    http.get('/api/interviews').subscribe();

    const req = httpTesting.expectOne('/api/interviews');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });
});
