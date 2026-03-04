import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatListModule } from '@angular/material/list';
import { InterviewService } from '../../core/services/interview.service';
import { AuthService } from '../../core/services/auth.service';
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
  private readonly authService = inject(AuthService);

  readonly interviews = signal<Interview[]>([]);
  readonly loading = signal(false);
  readonly totalInterviews = signal(0);
  readonly upcomingCount = signal(0);
  readonly pendingShadowingCount = signal(0);
  readonly completedCount = signal(0);
  readonly userName = computed(() => {
    const email = this.authService.email();
    return email
      ? email.split('@')[0].split('.').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ')
      : '';
  });

  ngOnInit(): void {
    this.loading.set(true);
    this.interviewService.list(0, 100, 'startTime,asc').subscribe({
      next: (page) => {
        const all = page.content;
        this.totalInterviews.set(page.totalElements);
        this.upcomingCount.set(all.filter(iv => iv.status === 'SCHEDULED').length);
        this.completedCount.set(all.filter(iv => iv.status === 'COMPLETED').length);
        this.interviews.set(all.filter(iv => iv.status === 'SCHEDULED').slice(0, 5));
        const pending = all.reduce(
          (acc, iv) => acc + (iv.shadowingRequests?.filter(sr => sr.status === 'PENDING').length ?? 0),
          0,
        );
        this.pendingShadowingCount.set(pending);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
