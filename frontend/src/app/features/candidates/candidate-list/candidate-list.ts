import { ChangeDetectionStrategy, Component, inject, signal, OnInit } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { CandidateService } from '../../../core/services/candidate.service';
import { Candidate } from '../../../core/models/candidate.model';
import { CandidateFormDialogComponent } from '../candidate-form-dialog/candidate-form-dialog';

@Component({
  selector: 'app-candidate-list',
  standalone: true,
  imports: [
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './candidate-list.html',
  styleUrl: './candidate-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CandidateListComponent implements OnInit {
  private readonly candidateService = inject(CandidateService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly displayedColumns = ['name', 'email', 'primaryArea', 'actions'];
  readonly candidates = signal<Candidate[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadCandidates();
  }

  loadCandidates(): void {
    this.loading.set(true);
    this.error.set(null);
    this.candidateService.list().subscribe({
      next: (candidates) => {
        this.candidates.set(candidates);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load candidates. Please try again.');
        this.loading.set(false);
      },
    });
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(CandidateFormDialogComponent, { width: '500px' });
    ref.afterClosed().subscribe((created) => {
      if (created) {
        this.snackBar.open('Candidate created successfully', 'Dismiss', { duration: 3000 });
        this.loadCandidates();
      }
    });
  }

  openEditDialog(candidate: Candidate): void {
    const ref = this.dialog.open(CandidateFormDialogComponent, {
      width: '500px',
      data: { candidate },
    });
    ref.afterClosed().subscribe((updated) => {
      if (updated) {
        this.snackBar.open('Candidate updated successfully', 'Dismiss', { duration: 3000 });
        this.loadCandidates();
      }
    });
  }

  deleteCandidate(candidate: Candidate): void {
    if (!confirm(`Delete candidate "${candidate.name}"?`)) return;
    this.candidateService.remove(candidate.id).subscribe({
      next: () => {
        this.snackBar.open('Candidate deleted', 'Dismiss', { duration: 3000 });
        this.loadCandidates();
      },
      error: () => {
        this.snackBar.open('Failed to delete candidate', 'Dismiss', { duration: 3000 });
      },
    });
  }
}
