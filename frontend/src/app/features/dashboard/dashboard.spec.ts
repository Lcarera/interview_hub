import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DashboardComponent } from './dashboard';

describe('DashboardComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
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
    const fixture = TestBed.createComponent(DashboardComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should load interviews on init', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    const req = httpTesting.expectOne(r => r.url.includes('/api/interviews'));
    expect(req.request.params.get('sort')).toBe('startTime,asc');
    expect(req.request.params.get('size')).toBe('5');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 });

    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('No upcoming interviews');
  });

  it('should display interviews when loaded', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    const req = httpTesting.expectOne(r => r.url.includes('/api/interviews'));
    req.flush({
      content: [
        {
          id: '1',
          techStack: 'Java',
          startTime: '2026-03-01T10:00:00Z',
          endTime: '2026-03-01T11:00:00Z',
          interviewer: { id: 'p-1', email: 'dev@gm2dev.com', role: 'interviewer' },
          candidateInfo: {},
          status: 'SCHEDULED',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 5,
    });

    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Java');
  });
});
