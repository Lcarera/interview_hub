import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { ShellComponent } from './shell';
import { AuthService } from '../../core/services/auth.service';

describe('ShellComponent', () => {
  let authService: AuthService;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [provideRouter([]), provideAnimationsAsync()],
    }).compileComponents();
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => localStorage.clear());

  it('should create', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should display the brand name', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.brand')?.textContent).toContain('Interview Hub');
  });

  it('should contain a router-outlet', () => {
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('router-outlet')).not.toBeNull();
  });

  it('should show user email', () => {
    authService.email.set('user@gm2dev.com');
    const fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('user@gm2dev.com');
  });
});
