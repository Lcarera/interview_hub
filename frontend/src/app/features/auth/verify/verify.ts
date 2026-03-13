import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-verify',
  standalone: true,
  imports: [RouterLink, MatCardModule, MatIconModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <div class="auth-page">
      <mat-card class="auth-card">
        @if (loading()) {
          <div class="centered"><mat-spinner /></div>
        } @else if (success()) {
          <div class="centered">
            <mat-icon class="status-icon success">check_circle</mat-icon>
            <h2>Email Verified!</h2>
            <p>Your email has been verified. You can now sign in.</p>
            <a mat-flat-button routerLink="/login">Go to Login</a>
          </div>
        } @else {
          <div class="centered">
            <mat-icon class="status-icon error">error</mat-icon>
            <h2>Verification Failed</h2>
            <p>{{ error() }}</p>
            <a mat-flat-button routerLink="/login">Go to Login</a>
          </div>
        }
      </mat-card>
    </div>
  `,
  styles: [`
    .auth-page { display: flex; justify-content: center; align-items: center; min-height: 100vh; }
    .auth-card { max-width: 420px; width: 100%; padding: 2rem; }
    .centered { text-align: center; }
    .status-icon { font-size: 48px; width: 48px; height: 48px; }
    .status-icon.success { color: #4caf50; }
    .status-icon.error { color: #f44336; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyComponent implements OnInit {
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);

  loading = signal(true);
  success = signal(false);
  error = signal('The verification link is invalid or has expired.');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.loading.set(false);
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: () => { this.loading.set(false); this.success.set(true); },
      error: () => { this.loading.set(false); },
    });
  }
}
