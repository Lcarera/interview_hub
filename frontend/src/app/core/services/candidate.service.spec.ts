import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CandidateService } from './candidate.service';
import { CandidateRequest } from '../models/candidate.model';

describe('CandidateService', () => {
  let service: CandidateService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CandidateService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should list all candidates', () => {
    service.list().subscribe();
    const req = httpTesting.expectOne(r => r.url.includes('/api/candidates'));
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should get a single candidate', () => {
    service.get('c-1').subscribe();
    const req = httpTesting.expectOne(r => r.url.endsWith('/api/candidates/c-1'));
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'c-1', name: 'Alice', email: 'alice@test.com' });
  });

  it('should create a candidate', () => {
    const body: CandidateRequest = { name: 'Alice', email: 'alice@test.com' };
    service.create(body).subscribe();
    const req = httpTesting.expectOne(r => r.url.includes('/api/candidates'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 'c-new', ...body });
  });

  it('should update a candidate', () => {
    const body: CandidateRequest = { name: 'Alice Updated', email: 'alice@test.com' };
    service.update('c-1', body).subscribe();
    const req = httpTesting.expectOne(r => r.url.endsWith('/api/candidates/c-1'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 'c-1', ...body });
  });

  it('should delete a candidate', () => {
    service.remove('c-1').subscribe();
    const req = httpTesting.expectOne(r => r.url.endsWith('/api/candidates/c-1'));
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
