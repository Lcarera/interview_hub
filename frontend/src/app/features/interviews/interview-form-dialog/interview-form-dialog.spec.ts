import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { InterviewFormDialogComponent } from './interview-form-dialog';
import { AuthService } from '../../../core/services/auth.service';

describe('InterviewFormDialogComponent', () => {
  const baseProviders = [
    provideAnimationsAsync(),
    provideHttpClient(),
    provideHttpClientTesting(),
    { provide: MatDialogRef, useValue: { close: vi.fn() } },
  ];

  afterEach(() => localStorage.clear());

  describe('create mode', () => {
    beforeEach(async () => {
      localStorage.clear();
      await TestBed.configureTestingModule({
        imports: [InterviewFormDialogComponent],
        providers: baseProviders,
      }).compileComponents();
    });

    it('should create in create mode', () => {
      const fixture = TestBed.createComponent(InterviewFormDialogComponent);
      expect(fixture.componentInstance).toBeTruthy();
      expect(fixture.componentInstance.isEdit).toBe(false);
    });

    it('should have required form fields', () => {
      const fixture = TestBed.createComponent(InterviewFormDialogComponent);
      const form = fixture.componentInstance.form;
      expect(form.controls.techStack).toBeTruthy();
      expect(form.controls.startTime).toBeTruthy();
      expect(form.controls.duration).toBeTruthy();
    });

    it('should default duration to 60 minutes', () => {
      const fixture = TestBed.createComponent(InterviewFormDialogComponent);
      expect(fixture.componentInstance.form.controls.duration.value).toBe(60);
    });

    it('should be invalid when required fields are empty', () => {
      const fixture = TestBed.createComponent(InterviewFormDialogComponent);
      expect(fixture.componentInstance.form.valid).toBe(false);
    });

    it('should be valid when required fields are filled', () => {
      const fixture = TestBed.createComponent(InterviewFormDialogComponent);
      const form = fixture.componentInstance.form;
      form.controls.techStack.setValue('Java');
      form.controls.startTime.setValue('2026-03-01T10:00');
      form.controls.duration.setValue(60);
      expect(form.valid).toBe(true);
    });

    it('should compute endTime from startTime + duration on save', () => {
      const fixture = TestBed.createComponent(InterviewFormDialogComponent);
      const httpTesting = TestBed.inject(HttpTestingController);

      const auth = TestBed.inject(AuthService);
      const payload = btoa(JSON.stringify({ sub: 'p-1' }));
      auth.handleCallback(`h.${payload}.s`, 'test@gm2dev.com', 3600);

      const form = fixture.componentInstance.form;
      form.controls.techStack.setValue('Java');
      form.controls.startTime.setValue('2026-03-01T10:00');
      form.controls.duration.setValue(90);

      fixture.componentInstance.save();

      const req = httpTesting.expectOne(r => r.url.includes('/api/interviews'));
      const body = req.request.body;
      const start = new Date(body.startTime);
      const end = new Date(body.endTime);
      expect(end.getTime() - start.getTime()).toBe(90 * 60_000);
      req.flush({ id: 'new-1' });
      httpTesting.verify();
    });
  });

  describe('edit mode', () => {
    const existingInterview = {
      id: 'iv-1',
      techStack: 'Python',
      candidateInfo: { name: 'Alice' },
      interviewer: { id: 'p-1', email: 'dev@gm2dev.com', role: 'interviewer' },
      startTime: '2026-03-01T10:00:00Z',
      endTime: '2026-03-01T10:45:00Z',
      status: 'SCHEDULED' as const,
    };

    beforeEach(async () => {
      localStorage.clear();
      await TestBed.configureTestingModule({
        imports: [InterviewFormDialogComponent],
        providers: [
          ...baseProviders,
          { provide: MAT_DIALOG_DATA, useValue: { interview: existingInterview } },
        ],
      }).compileComponents();
    });

    it('should be in edit mode', () => {
      const fixture = TestBed.createComponent(InterviewFormDialogComponent);
      expect(fixture.componentInstance.isEdit).toBe(true);
    });

    it('should derive duration from existing interview', () => {
      const fixture = TestBed.createComponent(InterviewFormDialogComponent);
      expect(fixture.componentInstance.form.controls.duration.value).toBe(45);
    });

    it('should pre-fill tech stack', () => {
      const fixture = TestBed.createComponent(InterviewFormDialogComponent);
      expect(fixture.componentInstance.form.controls.techStack.value).toBe('Python');
    });
  });
});
