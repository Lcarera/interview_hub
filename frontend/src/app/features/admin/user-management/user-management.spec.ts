import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { UserManagementComponent } from './user-management';

describe('UserManagementComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserManagementComponent],
      providers: [
        provideRouter([]),
        provideAnimationsAsync(),
        provideHttpClient(),
        provideHttpClientTesting(),
        MatDialog,
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should create', () => {
    const fixture = TestBed.createComponent(UserManagementComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should load users on init', () => {
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();

    const req = httpTesting.expectOne(r => r.url.includes('/admin/users'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({
      content: [{ id: 'u-1', email: 'admin@gm2dev.com', role: 'admin' }],
      totalElements: 1,
    });

    expect(fixture.componentInstance.users().length).toBe(1);
    expect(fixture.componentInstance.totalUsers()).toBe(1);
  });
});
