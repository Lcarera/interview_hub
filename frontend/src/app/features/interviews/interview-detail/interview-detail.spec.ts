import { TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InterviewDetailComponent } from './interview-detail';

const mockInterview = {
  id: 'iv-1',
  techStack: 'React',
  candidateInfo: { name: 'Bob', email: 'bob@example.com' },
  interviewer: { id: 'p-1', email: 'dev@gm2dev.com', role: 'interviewer' },
  startTime: '2026-03-01T10:00:00Z',
  endTime: '2026-03-01T11:00:00Z',
  status: 'SCHEDULED',
  shadowingRequests: [
    {
      id: 'sr-1',
      shadower: { id: 'p-2', email: 'shadow@gm2dev.com', role: 'interviewer' },
      status: 'PENDING',
    },
  ],
};

describe('InterviewDetailComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [InterviewDetailComponent],
      providers: [
        provideRouter([]),
        provideAnimationsAsync(),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'iv-1' } } },
        },
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(InterviewDetailComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should load and display interview details', () => {
    const fixture = TestBed.createComponent(InterviewDetailComponent);
    fixture.detectChanges();

    const req = httpTesting.expectOne(r => r.url.endsWith('/api/interviews/iv-1'));
    req.flush(mockInterview);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('React');
    expect(el.textContent).toContain('dev@gm2dev.com');
    expect(el.textContent).toContain('Bob');
  });

  it('should show shadowing requests', () => {
    const fixture = TestBed.createComponent(InterviewDetailComponent);
    fixture.detectChanges();

    const req = httpTesting.expectOne(r => r.url.endsWith('/api/interviews/iv-1'));
    req.flush(mockInterview);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('shadow@gm2dev.com');
    expect(el.textContent).toContain('PENDING');
  });
});
