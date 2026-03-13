import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
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
          <mat-card-title>Create Account</mat-card-title>
          <mat-card-subtitle>Register with your &#64;gm2dev.com email</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @if (success()) {
            <div class="success-message">
              <mat-icon>check_circle</mat-icon>
              <p>Registration successful! Check your email for a verification link.</p>
              <a mat-button routerLink="/login">Back to login</a>
            </div>
          } @else {
            @if (error()) {
              <div class="error-message">{{ error() }}</div>
            }
            <form (ngSubmit)="register()" class="auth-form">
              <mat-form-field appearance="outline">
                <mat-label>Email</mat-label>
                <input matInput type="email" [(ngModel)]="email" name="email" required />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Password</mat-label>
                <input matInput type="password" [(ngModel)]="password" name="password" required />
                <mat-hint>Min 8 chars, uppercase, lowercase, and a number</mat-hint>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Confirm Password</mat-label>
                <input matInput type="password" [(ngModel)]="confirmPassword" name="confirmPassword" required />
              </mat-form-field>
              <button mat-flat-button type="submit" [disabled]="loading()">
                @if (loading()) { <mat-spinner diameter="20" /> } @else { Register }
              </button>
            </form>
            <p class="form-link"><a routerLink="/login">Already have an account? Sign in</a></p>
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
    .error-message { background: #fdecea; color: #b71c1c; padding: 0.75rem; border-radius: 4px; margin-bottom: 0.5rem; font-size: 0.875rem; }
  `],
})
export class RegisterComponent {
  private auth = inject(AuthService);

  email = '';
  password = '';
  confirmPassword = '';
  loading = signal(false);
  error = signal<string | null>(null);
  success = signal(false);

  register(): void {
    if (this.password !== this.confirmPassword) {
      this.error.set('Passwords do not match');
      return;
    }
    if (!this.email.endsWith('@gm2dev.com')) {
      this.error.set('Only @gm2dev.com emails are allowed');
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.auth.register(this.email, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.status === 409
          ? 'An account with this email already exists'
          : err.status === 403
            ? 'Registration is restricted to @gm2dev.com accounts'
            : 'An error occurred. Please try again.');
      },
    });
  }
}
