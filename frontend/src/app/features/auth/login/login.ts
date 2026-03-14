import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    FormsModule, RouterLink,
    MatButtonModule, MatCardModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatDividerModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  password = '';
  loading = signal(false);
  error = signal<string | null>(null);

  loginWithEmail(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auth.loginWithEmail(this.email, this.password).subscribe({
      next: (response) => {
        this.auth.handleLoginResponse(response);
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.status === 403
          ? 'Invalid email or password'
          : 'An error occurred. Please try again.');
      },
    });
  }

  loginWithGoogle(): void {
    this.auth.loginWithGoogle();
  }
}
