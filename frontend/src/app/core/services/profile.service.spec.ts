import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProfileService } from './profile.service';

describe('ProfileService', () => {
  let service: ProfileService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProfileService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should list all profiles', () => {
    service.list().subscribe();
    const req = httpTesting.expectOne(r => r.url.includes('/api/profiles'));
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should get current user profile', () => {
    service.getMe().subscribe();
    const req = httpTesting.expectOne(r => r.url.endsWith('/api/profiles/me'));
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'p-1', email: 'user@gm2dev.com', role: 'interviewer' });
  });
});
