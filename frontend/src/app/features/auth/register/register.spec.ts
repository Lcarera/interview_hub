import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RegisterComponent } from './register';

describe('RegisterComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        provideRouter([]),
        provideAnimationsAsync(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should create', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show error when passwords do not match', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance;
    component.email = 'user@gm2dev.com';
    component.password = 'Password1';
    component.confirmPassword = 'Different1';

    component.register();

    expect(component.error()).toBe('Passwords do not match');
  });

  it('should show error for invalid domain email', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance;
    component.email = 'user@gmail.com';
    component.password = 'Password1';
    component.confirmPassword = 'Password1';

    component.register();

    expect(component.error()).toBe('Only @gm2dev.com and @lcarera.dev emails are allowed');
  });

  it('should call register on valid submit', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance;
    component.email = 'user@gm2dev.com';
    component.password = 'Password1';
    component.confirmPassword = 'Password1';

    component.register();

    const req = httpTesting.expectOne(r => r.url.includes('/auth/register'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'user@gm2dev.com', password: 'Password1' });
    req.flush(null);

    expect(component.success()).toBe(true);
  });
});
