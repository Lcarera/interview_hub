import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { CandidateService } from '../../../core/services/candidate.service';
import { Candidate } from '../../../core/models/candidate.model';

export interface CandidateFormDialogData {
  candidate?: Candidate;
}

@Component({
  selector: 'app-candidate-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  templateUrl: './candidate-form-dialog.html',
  styleUrl: './candidate-form-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CandidateFormDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly candidateService = inject(CandidateService);
  private readonly dialogRef = inject(MatDialogRef<CandidateFormDialogComponent>);
  readonly data: CandidateFormDialogData | null = inject(MAT_DIALOG_DATA, { optional: true });

  readonly isEdit = !!this.data?.candidate;

  readonly form = this.fb.nonNullable.group({
    name: [this.data?.candidate?.name ?? '', Validators.required],
    email: [this.data?.candidate?.email ?? '', [Validators.required, Validators.email]],
    linkedinUrl: [this.data?.candidate?.linkedinUrl ?? ''],
    primaryArea: [this.data?.candidate?.primaryArea ?? ''],
    feedbackLink: [this.data?.candidate?.feedbackLink ?? ''],
  });

  readonly submitting = signal(false);

  save(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);

    const v = this.form.getRawValue();
    const body = {
      name: v.name,
      email: v.email,
      linkedinUrl: v.linkedinUrl || undefined,
      primaryArea: v.primaryArea || undefined,
      feedbackLink: v.feedbackLink || undefined,
    };

    const request$ = this.isEdit
      ? this.candidateService.update(this.data!.candidate!.id, body)
      : this.candidateService.create(body);

    request$.subscribe({
      next: (result) => this.dialogRef.close(result),
      error: () => this.submitting.set(false),
    });
  }
}
