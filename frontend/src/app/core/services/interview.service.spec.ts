import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InterviewService } from './interview.service';

describe('InterviewService', () => {
  let service: InterviewService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InterviewService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should list interviews with default pagination', () => {
    service.list().subscribe();
    const req = httpTesting.expectOne(r => r.url.includes('/api/interviews'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });

  it('should pass sort parameter when provided', () => {
    service.list(0, 10, 'startTime,asc').subscribe();
    const req = httpTesting.expectOne(r => r.url.includes('/api/interviews'));
    expect(req.request.params.get('sort')).toBe('startTime,asc');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });

  it('should get a single interview', () => {
    service.get('abc-123').subscribe();
    const req = httpTesting.expectOne(r => r.url.endsWith('/api/interviews/abc-123'));
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'abc-123' });
  });

  it('should create an interview', () => {
    const body = {
      interviewerId: 'p-1',
      techStack: 'Java',
      startTime: '2026-03-01T10:00:00Z',
      endTime: '2026-03-01T11:00:00Z',
    };
    service.create(body).subscribe();
    const req = httpTesting.expectOne(r => r.url.includes('/api/interviews'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 'new-1', ...body });
  });

  it('should update an interview', () => {
    const body = {
      techStack: 'Python',
      startTime: '2026-03-01T10:00:00Z',
      endTime: '2026-03-01T11:00:00Z',
      status: 'SCHEDULED' as const,
    };
    service.update('abc-123', body).subscribe();
    const req = httpTesting.expectOne(r => r.url.endsWith('/api/interviews/abc-123'));
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 'abc-123', ...body });
  });

  it('should delete an interview', () => {
    service.remove('abc-123').subscribe();
    const req = httpTesting.expectOne(r => r.url.endsWith('/api/interviews/abc-123'));
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
