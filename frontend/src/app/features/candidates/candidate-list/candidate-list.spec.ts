import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CandidateListComponent } from './candidate-list';

const mockCandidates = [
  { id: 'c-1', name: 'Alice', email: 'alice@test.com', primaryArea: 'Backend' },
  { id: 'c-2', name: 'Bob', email: 'bob@test.com' },
];

describe('CandidateListComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CandidateListComponent],
      providers: [
        provideAnimationsAsync(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should create', () => {
    const fixture = TestBed.createComponent(CandidateListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should load and display candidates', () => {
    const fixture = TestBed.createComponent(CandidateListComponent);
    fixture.detectChanges();

    const req = httpTesting.expectOne(r => r.url.includes('/api/candidates'));
    expect(req.request.method).toBe('GET');
    req.flush(mockCandidates);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Alice');
    expect(el.textContent).toContain('alice@test.com');
    expect(el.textContent).toContain('Backend');
    expect(el.textContent).toContain('Bob');
  });

  it('should show spinner while loading', () => {
    const fixture = TestBed.createComponent(CandidateListComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('mat-spinner')).not.toBeNull();

    const req = httpTesting.expectOne(r => r.url.includes('/api/candidates'));
    req.flush(mockCandidates);
    fixture.detectChanges();
    expect(el.querySelector('mat-spinner')).toBeNull();
  });

  it('should show empty state when no candidates', () => {
    const fixture = TestBed.createComponent(CandidateListComponent);
    fixture.detectChanges();

    const req = httpTesting.expectOne(r => r.url.includes('/api/candidates'));
    req.flush([]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('No candidates yet');
  });

  it('should show error state on failure', () => {
    const fixture = TestBed.createComponent(CandidateListComponent);
    fixture.detectChanges();

    const req = httpTesting.expectOne(r => r.url.includes('/api/candidates'));
    req.flush('error', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Failed to load candidates');
  });
});
