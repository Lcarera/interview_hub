import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { ResetPasswordComponent } from './reset-password';

describe('ResetPasswordComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [
        provideRouter([]),
        provideAnimationsAsync(),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => (key === 'token' ? 'reset-token' : null),
              },
            },
          },
        },
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should create', () => {
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show error when passwords do not match', () => {
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    component.newPassword = 'Password1';
    component.confirmPassword = 'Different1';
    component.submit();

    expect(component.error()).toBe('Passwords do not match');
  });

  it('should call resetPassword on valid submit', () => {
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    component.newPassword = 'NewPassword1';
    component.confirmPassword = 'NewPassword1';
    component.submit();

    const req = httpTesting.expectOne(r => r.url.includes('/auth/reset-password'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'reset-token', newPassword: 'NewPassword1' });
    req.flush(null);

    expect(component.success()).toBe(true);
  });
});
