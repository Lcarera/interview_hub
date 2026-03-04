import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { InterviewService } from '../../../core/services/interview.service';
import { Interview } from '../../../core/models/interview.model';
import { Page } from '../../../core/models/page.model';
import { InterviewFormDialogComponent } from '../interview-form-dialog/interview-form-dialog';

@Component({
  selector: 'app-interview-list',
  standalone: true,
  imports: [
    DatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatChipsModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './interview-list.html',
  styleUrl: './interview-list.scss',
})
export class InterviewListComponent implements OnInit {
  private readonly interviewService = inject(InterviewService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  readonly displayedColumns = ['techStack', 'candidateName', 'interviewer', 'dateTime', 'status'];
  readonly page = signal<Page<Interview> | null>(null);
  readonly loading = signal(false);

  pageIndex = 0;
  pageSize = 20;
  sortActive = '';
  sortDirection = '';

  ngOnInit(): void {
    this.loadInterviews();
  }

  loadInterviews(): void {
    this.loading.set(true);
    const sort = this.sortActive && this.sortDirection
      ? `${this.sortActive},${this.sortDirection}`
      : undefined;
    this.interviewService.list(this.pageIndex, this.pageSize, sort).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadInterviews();
  }

  onSortChange(sort: Sort): void {
    this.sortActive = sort.active;
    this.sortDirection = sort.direction;
    this.pageIndex = 0;
    this.loadInterviews();
  }

  onRowClick(interview: Interview): void {
    this.router.navigate(['/interviews', interview.id]);
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(InterviewFormDialogComponent, {
      width: '500px',
    });
    ref.afterClosed().subscribe((created) => {
      if (created) this.loadInterviews();
    });
  }

  statusColor(status: string): string {
    switch (status) {
      case 'SCHEDULED': return 'primary';
      case 'COMPLETED': return 'accent';
      case 'CANCELLED': return 'warn';
      default: return '';
    }
  }
}
