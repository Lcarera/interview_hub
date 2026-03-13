import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminService, CreateUserRequest } from './admin.service';

describe('AdminService', () => {
  let service: AdminService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should list users with pagination params', () => {
    service.listUsers(1, 10).subscribe();
    const req = httpTesting.expectOne(r => r.url.includes('/admin/users'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ content: [], totalElements: 0 });
  });

  it('should create a user', () => {
    const body: CreateUserRequest = { email: 'new@gm2dev.com', role: 'interviewer' };
    service.createUser(body).subscribe();
    const req = httpTesting.expectOne(r => r.url.includes('/admin/users'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 'u-1', email: 'new@gm2dev.com', role: 'interviewer' });
  });

  it('should update a user role', () => {
    service.updateRole('u-1', 'admin').subscribe();
    const req = httpTesting.expectOne(r => r.url.endsWith('/admin/users/u-1/role'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ role: 'admin' });
    req.flush(null);
  });

  it('should delete a user', () => {
    service.deleteUser('u-1').subscribe();
    const req = httpTesting.expectOne(r => r.url.endsWith('/admin/users/u-1'));
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
