import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatListModule } from '@angular/material/list';
import { InterviewService } from '../../core/services/interview.service';
import { Interview } from '../../core/models/interview.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatListModule,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardComponent implements OnInit {
  private readonly interviewService = inject(InterviewService);

  readonly interviews = signal<Interview[]>([]);
  readonly loading = signal(false);
  readonly pendingShadowingCount = signal(0);

  ngOnInit(): void {
    this.loading.set(true);
    this.interviewService.list(0, 5, 'startTime,asc').subscribe({
      next: (page) => {
        this.interviews.set(page.content);
        const count = page.content.reduce((acc, iv) => {
          return acc + (iv.shadowingRequests?.filter(sr => sr.status === 'PENDING').length ?? 0);
        }, 0);
        this.pendingShadowingCount.set(count);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
