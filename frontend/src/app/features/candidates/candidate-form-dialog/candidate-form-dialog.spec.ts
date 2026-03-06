import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { CandidateFormDialogComponent } from './candidate-form-dialog';

describe('CandidateFormDialogComponent', () => {
  const baseProviders = [
    provideAnimationsAsync(),
    provideHttpClient(),
    provideHttpClientTesting(),
    { provide: MatDialogRef, useValue: { close: vi.fn() } },
  ];

  afterEach(() => {
    const httpTesting = TestBed.inject(HttpTestingController);
    httpTesting.match(() => true);
  });

  describe('create mode', () => {
    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [CandidateFormDialogComponent],
        providers: baseProviders,
      }).compileComponents();
    });

    it('should create in create mode', () => {
      const fixture = TestBed.createComponent(CandidateFormDialogComponent);
      expect(fixture.componentInstance).toBeTruthy();
      expect(fixture.componentInstance.isEdit).toBe(false);
    });

    it('should be invalid when required fields are empty', () => {
      const fixture = TestBed.createComponent(CandidateFormDialogComponent);
      expect(fixture.componentInstance.form.valid).toBe(false);
    });

    it('should be invalid with bad email', () => {
      const fixture = TestBed.createComponent(CandidateFormDialogComponent);
      const form = fixture.componentInstance.form;
      form.controls.name.setValue('Alice');
      form.controls.email.setValue('not-an-email');
      expect(form.valid).toBe(false);
    });

    it('should be valid when name and email are filled', () => {
      const fixture = TestBed.createComponent(CandidateFormDialogComponent);
      const form = fixture.componentInstance.form;
      form.controls.name.setValue('Alice');
      form.controls.email.setValue('alice@test.com');
      expect(form.valid).toBe(true);
    });

    it('should POST on save in create mode', () => {
      const fixture = TestBed.createComponent(CandidateFormDialogComponent);
      const httpTesting = TestBed.inject(HttpTestingController);

      const form = fixture.componentInstance.form;
      form.controls.name.setValue('Alice');
      form.controls.email.setValue('alice@test.com');
      form.controls.primaryArea.setValue('Backend');

      fixture.componentInstance.save();

      const req = httpTesting.expectOne(r => r.url.includes('/api/candidates'));
      expect(req.request.method).toBe('POST');
      expect(req.request.body.name).toBe('Alice');
      expect(req.request.body.email).toBe('alice@test.com');
      expect(req.request.body.primaryArea).toBe('Backend');
      expect(req.request.body.linkedinUrl).toBeUndefined();
      req.flush({ id: 'c-new', name: 'Alice', email: 'alice@test.com' });
    });

    it('should not save when form is invalid', () => {
      const fixture = TestBed.createComponent(CandidateFormDialogComponent);
      const httpTesting = TestBed.inject(HttpTestingController);

      fixture.componentInstance.save();

      httpTesting.expectNone(r => r.url.includes('/api/candidates'));
    });
  });

  describe('edit mode', () => {
    const existingCandidate = {
      id: 'c-1',
      name: 'Bob',
      email: 'bob@test.com',
      primaryArea: 'Frontend',
    };

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [CandidateFormDialogComponent],
        providers: [
          ...baseProviders,
          { provide: MAT_DIALOG_DATA, useValue: { candidate: existingCandidate } },
        ],
      }).compileComponents();
    });

    it('should be in edit mode', () => {
      const fixture = TestBed.createComponent(CandidateFormDialogComponent);
      expect(fixture.componentInstance.isEdit).toBe(true);
    });

    it('should pre-fill form fields', () => {
      const fixture = TestBed.createComponent(CandidateFormDialogComponent);
      const form = fixture.componentInstance.form;
      expect(form.controls.name.value).toBe('Bob');
      expect(form.controls.email.value).toBe('bob@test.com');
      expect(form.controls.primaryArea.value).toBe('Frontend');
    });

    it('should PUT on save in edit mode', () => {
      const fixture = TestBed.createComponent(CandidateFormDialogComponent);
      const httpTesting = TestBed.inject(HttpTestingController);

      fixture.componentInstance.save();

      const req = httpTesting.expectOne(r => r.url.endsWith('/api/candidates/c-1'));
      expect(req.request.method).toBe('PUT');
      expect(req.request.body.name).toBe('Bob');
      req.flush({ ...existingCandidate });
    });
  });
});
