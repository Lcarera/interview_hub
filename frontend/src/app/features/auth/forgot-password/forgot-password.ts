import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-forgot-password',
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
          <mat-card-title>Forgot Password</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (sent()) {
            <div class="success-message">
              <mat-icon>check_circle</mat-icon>
              <p>If an account exists with that email, a reset link has been sent.</p>
              <a mat-button routerLink="/login">Back to login</a>
            </div>
          } @else {
            <form (ngSubmit)="submit()" class="auth-form">
              <mat-form-field appearance="outline">
                <mat-label>Email</mat-label>
                <input matInput type="email" [(ngModel)]="email" name="email" required />
              </mat-form-field>
              <button mat-flat-button type="submit" [disabled]="loading()">
                @if (loading()) { <mat-spinner diameter="20" /> } @else { Send Reset Link }
              </button>
            </form>
            <p class="form-link"><a routerLink="/login">Back to login</a></p>
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
    .form-link { text-align: center; margin-top: 1rem; font-size: 0.85rem; }
    .form-link a { color: var(--telus-purple); text-decoration: none; }
    .success-message { text-align: center; padding: 1rem; }
    .success-message mat-icon { font-size: 48px; width: 48px; height: 48px; color: #4caf50; }
  `],
})
export class ForgotPasswordComponent {
  private auth = inject(AuthService);

  email = '';
  loading = signal(false);
  sent = signal(false);

  submit(): void {
    this.loading.set(true);
    this.auth.forgotPassword(this.email).subscribe({
      next: () => { this.loading.set(false); this.sent.set(true); },
      error: () => { this.loading.set(false); this.sent.set(true); },
    });
  }
}
