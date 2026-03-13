import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [
    FormsModule, RouterLink,
    MatButtonModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatIconModule, MatProgressSpinnerModule,
  ],
  template: `
    <div class="auth-page">
      <mat-card class="auth-card">
        <mat-card-header>
          <mat-card-title>Reset Password</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (success()) {
            <div class="success-message">
              <mat-icon>check_circle</mat-icon>
              <p>Password reset successfully!</p>
              <a mat-flat-button routerLink="/login">Go to Login</a>
            </div>
          } @else {
            @if (error()) {
              <div class="error-message">{{ error() }}</div>
            }
            <form (ngSubmit)="submit()" class="auth-form">
              <mat-form-field appearance="outline">
                <mat-label>New Password</mat-label>
                <input matInput type="password" [(ngModel)]="newPassword" name="newPassword" required />
                <mat-hint>Min 8 chars, uppercase, lowercase, and a number</mat-hint>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Confirm Password</mat-label>
                <input matInput type="password" [(ngModel)]="confirmPassword" name="confirmPassword" required />
              </mat-form-field>
              <button mat-flat-button type="submit" [disabled]="loading()">
                @if (loading()) { <mat-spinner diameter="20" /> } @else { Reset Password }
              </button>
            </form>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .auth-page { display: flex; justify-content: center; align-items: center; min-height: 100vh; padding: 1rem; }
    .auth-card { max-width: 420px; width: 100%; padding: 1.5rem; }
    .auth-form { display: flex; flex-direction: column; gap: 0.5rem; margin-top: 1rem; }
    .auth-form mat-form-field { width: 100%; }
    .auth-form button { height: 48px; }
    .success-message { text-align: center; padding: 1rem; }
    .success-message mat-icon { font-size: 48px; width: 48px; height: 48px; color: #4caf50; }
    .error-message { background: #fdecea; color: #b71c1c; padding: 0.75rem; border-radius: 4px; margin-bottom: 0.5rem; font-size: 0.875rem; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPasswordComponent implements OnInit {
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);

  token = '';
  newPassword = '';
  confirmPassword = '';
  loading = signal(false);
  success = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
  }

  submit(): void {
    if (this.newPassword !== this.confirmPassword) {
      this.error.set('Passwords do not match');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.resetPassword(this.token, this.newPassword).subscribe({
      next: () => { this.loading.set(false); this.success.set(true); },
      error: () => {
        this.loading.set(false);
        this.error.set('The reset link is invalid or has expired.');
      },
    });
  }
}
