import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { VerifyComponent } from './verify';

describe('VerifyComponent', () => {
  let httpTesting: HttpTestingController;

  function setup(token: string | null) {
    TestBed.configureTestingModule({
      imports: [VerifyComponent],
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
                get: (key: string) => (key === 'token' ? token : null),
              },
            },
          },
        },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpTesting.verify());

  it('should create', () => {
    setup('test-token');
    const fixture = TestBed.createComponent(VerifyComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should call verifyEmail with token from query params', () => {
    setup('test-token');
    const fixture = TestBed.createComponent(VerifyComponent);
    fixture.detectChanges();

    const req = httpTesting.expectOne(r => r.url.includes('/auth/verify') && r.params.get('token') === 'test-token');
    expect(req.request.method).toBe('GET');
    req.flush(null);
  });

  it('should show success on successful verification', () => {
    setup('test-token');
    const fixture = TestBed.createComponent(VerifyComponent);
    fixture.detectChanges();

    const req = httpTesting.expectOne(r => r.url.includes('/auth/verify'));
    req.flush(null);

    expect(fixture.componentInstance.success()).toBe(true);
    expect(fixture.componentInstance.loading()).toBe(false);
  });

  it('should show error on failed verification', () => {
    setup('test-token');
    const fixture = TestBed.createComponent(VerifyComponent);
    fixture.detectChanges();

    const req = httpTesting.expectOne(r => r.url.includes('/auth/verify'));
    req.flush(null, { status: 400, statusText: 'Bad Request' });

    expect(fixture.componentInstance.success()).toBe(false);
    expect(fixture.componentInstance.loading()).toBe(false);
  });
});
