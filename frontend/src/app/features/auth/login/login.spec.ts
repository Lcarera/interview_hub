import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LoginComponent } from './login';
import { AuthService } from '../../../core/services/auth.service';

describe('LoginComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
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
    const fixture = TestBed.createComponent(LoginComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should call loginWithEmail on form submit', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const component = fixture.componentInstance;
    component.email = 'test@gm2dev.com';
    component.password = 'Password1';

    component.loginWithEmail();

    const req = httpTesting.expectOne(r => r.url.includes('/auth/login'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'test@gm2dev.com', password: 'Password1' });
    req.flush({ token: 'jwt-token', expiresIn: 3600, email: 'test@gm2dev.com' });
  });

  it('should call loginWithGoogle', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const authService = TestBed.inject(AuthService);
    const spy = vi.spyOn(authService, 'loginWithGoogle').mockImplementation(() => {});

    fixture.componentInstance.loginWithGoogle();

    expect(spy).toHaveBeenCalled();
  });
});
