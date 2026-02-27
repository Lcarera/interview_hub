import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ShadowingRequestService } from './shadowing-request.service';

describe('ShadowingRequestService', () => {
  let service: ShadowingRequestService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ShadowingRequestService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should request shadowing for an interview', () => {
    service.requestShadowing('iv-1').subscribe();
    const req = httpTesting.expectOne(r =>
      r.url.endsWith('/api/interviews/iv-1/shadowing-requests')
    );
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'sr-1', status: 'PENDING' });
  });

  it('should approve a shadowing request', () => {
    service.approve('sr-1').subscribe();
    const req = httpTesting.expectOne(r =>
      r.url.endsWith('/api/shadowing-requests/sr-1/approve')
    );
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'sr-1', status: 'APPROVED' });
  });

  it('should reject a shadowing request with reason', () => {
    service.reject('sr-1', 'Not enough experience').subscribe();
    const req = httpTesting.expectOne(r =>
      r.url.endsWith('/api/shadowing-requests/sr-1/reject')
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Not enough experience' });
    req.flush({ id: 'sr-1', status: 'REJECTED', reason: 'Not enough experience' });
  });

  it('should cancel a shadowing request', () => {
    service.cancel('sr-1').subscribe();
    const req = httpTesting.expectOne(r =>
      r.url.endsWith('/api/shadowing-requests/sr-1/cancel')
    );
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'sr-1', status: 'CANCELLED' });
  });
});
