import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatListModule } from '@angular/material/list';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { InterviewService } from '../../../core/services/interview.service';
import { ShadowingRequestService } from '../../../core/services/shadowing-request.service';
import { AuthService } from '../../../core/services/auth.service';
import { Interview } from '../../../core/models/interview.model';
import { InterviewFormDialogComponent } from '../interview-form-dialog/interview-form-dialog';
import { RejectDialogComponent } from '../../shadowing/reject-dialog/reject-dialog';

@Component({
  selector: 'app-interview-detail',
  standalone: true,
  imports: [
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatListModule,
    MatDividerModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './interview-detail.html',
  styleUrl: './interview-detail.scss',
})
export class InterviewDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly interviewService = inject(InterviewService);
  private readonly shadowingService = inject(ShadowingRequestService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  readonly interview = signal<Interview | null>(null);
  readonly loading = signal(false);
  readonly profileId = this.authService.profileId;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.load(id);
  }

  load(id: string): void {
    this.loading.set(true);
    this.interviewService.get(id).subscribe({
      next: (interview) => {
        this.interview.set(interview);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.router.navigate(['/interviews']);
      },
    });
  }

  isOwner(): boolean {
    const iv = this.interview();
    return !!iv && iv.interviewer.id === this.profileId();
  }

  openEditDialog(): void {
    const iv = this.interview();
    if (!iv) return;
    const ref = this.dialog.open(InterviewFormDialogComponent, {
      width: '500px',
      data: { interview: iv },
    });
    ref.afterClosed().subscribe((updated) => {
      if (updated) this.interview.set(updated);
    });
  }

  requestShadow(): void {
    const iv = this.interview();
    if (!iv) return;
    this.shadowingService.requestShadowing(iv.id).subscribe(() => this.load(iv.id));
  }

  cancelInterview(): void {
    const iv = this.interview();
    if (!iv) return;
    this.interviewService.remove(iv.id).subscribe(() => this.router.navigate(['/interviews']));
  }

  approveShadowing(requestId: string): void {
    this.shadowingService.approve(requestId).subscribe(() => this.load(this.interview()!.id));
  }

  rejectShadowing(requestId: string): void {
    const ref = this.dialog.open(RejectDialogComponent, { width: '400px' });
    ref.afterClosed().subscribe((reason: string | undefined) => {
      if (reason) {
        this.shadowingService.reject(requestId, reason).subscribe(() => this.load(this.interview()!.id));
      }
    });
  }

  cancelShadowing(requestId: string): void {
    this.shadowingService.cancel(requestId).subscribe(() => this.load(this.interview()!.id));
  }
}
