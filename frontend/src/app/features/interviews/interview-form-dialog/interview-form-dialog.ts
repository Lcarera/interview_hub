import { Component, inject, signal, OnInit } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { InterviewService } from '../../../core/services/interview.service';
import { AuthService } from '../../../core/services/auth.service';
import { CandidateService } from '../../../core/services/candidate.service';
import { ProfileService } from '../../../core/services/profile.service';
import { Interview } from '../../../core/models/interview.model';
import { Candidate } from '../../../core/models/candidate.model';
import { Profile } from '../../../core/models/profile.model';

export interface InterviewFormDialogData {
  interview?: Interview;
}

@Component({
  selector: 'app-interview-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  templateUrl: './interview-form-dialog.html',
  styleUrl: './interview-form-dialog.scss',
})
export class InterviewFormDialogComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly interviewService = inject(InterviewService);
  private readonly authService = inject(AuthService);
  private readonly candidateService = inject(CandidateService);
  private readonly profileService = inject(ProfileService);
  private readonly dialogRef = inject(MatDialogRef<InterviewFormDialogComponent>);
  readonly data: InterviewFormDialogData | null = inject(MAT_DIALOG_DATA, { optional: true });

  readonly isEdit = !!this.data?.interview;
  readonly candidates = signal<Candidate[]>([]);
  readonly profiles = signal<Profile[]>([]);
  readonly loadError = signal<string | null>(null);
  readonly durationOptions = [
    { value: 30, label: '30 minutes' },
    { value: 45, label: '45 minutes' },
    { value: 60, label: '1 hour' },
    { value: 90, label: '1.5 hours' },
    { value: 120, label: '2 hours' },
  ];

  readonly form = this.fb.nonNullable.group({
    techStack: [this.data?.interview?.techStack ?? '', Validators.required],
    candidateId: [this.data?.interview?.candidate?.id ?? '', Validators.required],
    talentAcquisitionId: [this.data?.interview?.talentAcquisition?.id ?? ''],
    startTime: [this.toDatetimeLocal(this.data?.interview?.startTime), Validators.required],
    duration: [this.deriveDuration(this.data?.interview), Validators.required],
  });

  submitting = false;

  ngOnInit(): void {
    this.loadDropdownData();
  }

  loadDropdownData(): void {
    this.loadError.set(null);
    this.candidateService.list().subscribe({
      next: c => this.candidates.set(c),
      error: () => this.loadError.set('Failed to load candidates.'),
    });
    this.profileService.list().subscribe({
      next: p => this.profiles.set(p),
      error: () => this.loadError.set('Failed to load profiles.'),
    });
  }

  save(): void {
    if (this.form.invalid || this.submitting) return;
    this.submitting = true;

    const v = this.form.getRawValue();
    const start = new Date(v.startTime);
    const end = new Date(start.getTime() + v.duration * 60_000);

    if (this.isEdit) {
      this.interviewService.update(this.data!.interview!.id, {
        candidateId: v.candidateId,
        talentAcquisitionId: v.talentAcquisitionId || undefined,
        techStack: v.techStack,
        startTime: start.toISOString(),
        endTime: end.toISOString(),
        status: this.data!.interview!.status,
      }).subscribe({
        next: (updated) => this.dialogRef.close(updated),
        error: () => this.submitting = false,
      });
    } else {
      const profileId = this.authService.profileId();
      if (!profileId) return;
      this.interviewService.create({
        interviewerId: profileId,
        candidateId: v.candidateId,
        talentAcquisitionId: v.talentAcquisitionId || undefined,
        techStack: v.techStack,
        startTime: start.toISOString(),
        endTime: end.toISOString(),
      }).subscribe({
        next: (created) => this.dialogRef.close(created),
        error: () => this.submitting = false,
      });
    }
  }

  private toDatetimeLocal(iso: string | undefined): string {
    if (!iso) return '';
    const d = new Date(iso);
    const offset = d.getTimezoneOffset();
    const local = new Date(d.getTime() - offset * 60000);
    return local.toISOString().slice(0, 16);
  }

  private deriveDuration(interview: Interview | undefined): number {
    if (!interview) return 60;
    const diffMs = new Date(interview.endTime).getTime() - new Date(interview.startTime).getTime();
    const diffMin = Math.round(diffMs / 60_000);
    const match = this.durationOptions.find(o => o.value === diffMin);
    return match ? diffMin : 60;
  }
}
