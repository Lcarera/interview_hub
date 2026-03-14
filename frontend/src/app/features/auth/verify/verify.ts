import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-verify',
  standalone: true,
  imports: [
    FormsModule, RouterLink,
    MatCardModule, MatIconModule, MatButtonModule,
    MatFormFieldModule, MatInputModule, MatProgressSpinnerModule,
  ],
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
        } @else if (resendSuccess()) {
          <div class="centered">
            <mat-icon class="status-icon success">mark_email_read</mat-icon>
            <h2>Verification Email Sent</h2>
            <p>Check your inbox for a new verification link.</p>
            <a mat-button routerLink="/login">Back to login</a>
          </div>
        } @else {
          <div class="centered">
            <mat-icon class="status-icon error">error</mat-icon>
            <h2>Verification Failed</h2>
            <p>{{ error() }}</p>
            <div class="resend-section">
              <p>Enter your email to receive a new verification link:</p>
              <mat-form-field appearance="outline">
                <mat-label>Email</mat-label>
                <input matInput type="email" [(ngModel)]="resendEmail" name="resendEmail" />
              </mat-form-field>
              <button mat-flat-button (click)="resend()" [disabled]="resending()">
                @if (resending()) { <mat-spinner diameter="20" /> } @else { Resend Verification }
              </button>
            </div>
            <a mat-button routerLink="/login">Back to login</a>
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
    .resend-section { margin: 1.5rem 0 1rem; }
    .resend-section mat-form-field { width: 100%; }
    .resend-section button { width: 100%; height: 44px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyComponent implements OnInit {
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);

  loading = signal(true);
  success = signal(false);
  resendSuccess = signal(false);
  resending = signal(false);
  error = signal('The verification link is invalid or has expired.');
  resendEmail = '';

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

  resend(): void {
    if (!this.resendEmail) return;
    this.resending.set(true);
    this.auth.resendVerification(this.resendEmail).subscribe({
      next: () => { this.resending.set(false); this.resendSuccess.set(true); },
      error: () => { this.resending.set(false); },
    });
  }
}
