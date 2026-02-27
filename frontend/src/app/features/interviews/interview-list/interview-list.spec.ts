import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InterviewListComponent } from './interview-list';

const mockPage = {
  content: [
    {
      id: '1',
      techStack: 'Angular',
      candidateInfo: { name: 'Jane Doe' },
      interviewer: { id: 'p-1', email: 'dev@gm2dev.com', role: 'interviewer' },
      startTime: '2026-03-01T10:00:00Z',
      endTime: '2026-03-01T11:00:00Z',
      status: 'SCHEDULED',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

describe('InterviewListComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InterviewListComponent],
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
    const fixture = TestBed.createComponent(InterviewListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should load interviews on init', () => {
    const fixture = TestBed.createComponent(InterviewListComponent);
    fixture.detectChanges();

    const req = httpTesting.expectOne(r => r.url.includes('/api/interviews'));
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);

    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Angular');
    expect(el.textContent).toContain('dev@gm2dev.com');
  });

  it('should show spinner while loading', () => {
    const fixture = TestBed.createComponent(InterviewListComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('mat-spinner')).not.toBeNull();

    const req = httpTesting.expectOne(r => r.url.includes('/api/interviews'));
    req.flush(mockPage);
    fixture.detectChanges();
    expect(el.querySelector('mat-spinner')).toBeNull();
  });
});
