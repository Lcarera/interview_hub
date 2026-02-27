import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(AuthService);
  });

  afterEach(() => localStorage.clear());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should not be authenticated initially', () => {
    expect(service.isAuthenticated()).toBe(false);
  });

  it('should store token and email on handleCallback', () => {
    // Create a minimal JWT with a sub claim
    const payload = btoa(JSON.stringify({ sub: 'profile-123', email: 'test@gm2dev.com' }));
    const fakeToken = `header.${payload}.signature`;

    service.handleCallback(fakeToken, 'test@gm2dev.com', 3600);

    expect(service.getToken()).toBe(fakeToken);
    expect(service.email()).toBe('test@gm2dev.com');
    expect(service.profileId()).toBe('profile-123');
    expect(service.isAuthenticated()).toBe(true);
  });

  it('should clear all data on logout', () => {
    const payload = btoa(JSON.stringify({ sub: 'profile-123' }));
    const fakeToken = `header.${payload}.signature`;
    service.handleCallback(fakeToken, 'test@gm2dev.com', 3600);

    service.logout();

    expect(service.getToken()).toBeNull();
    expect(service.email()).toBeNull();
    expect(service.profileId()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('should detect expired tokens', () => {
    const payload = btoa(JSON.stringify({ sub: 'p-1' }));
    const fakeToken = `h.${payload}.s`;
    service.handleCallback(fakeToken, 'test@gm2dev.com', -1);

    expect(service.isAuthenticated()).toBe(false);
  });

  it('should handle malformed JWT gracefully', () => {
    service.handleCallback('not-a-jwt', 'test@gm2dev.com', 3600);

    expect(service.email()).toBe('test@gm2dev.com');
    expect(service.profileId()).toBeNull();
  });
});
