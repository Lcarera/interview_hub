import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
  ],
  template: `
    <mat-toolbar color="primary">
      <span class="brand">Interview Hub</span>

      <nav class="nav-links">
        <a mat-button routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Dashboard</a>
        <a mat-button routerLink="/interviews" routerLinkActive="active">Interviews</a>
      </nav>

      <span class="spacer"></span>

      <button mat-button [matMenuTriggerFor]="userMenu">
        <mat-icon>account_circle</mat-icon>
        {{ email() }}
      </button>
      <mat-menu #userMenu="matMenu">
        <button mat-menu-item (click)="logout()">
          <mat-icon>logout</mat-icon>
          <span>Logout</span>
        </button>
      </mat-menu>
    </mat-toolbar>

    <main class="shell-content">
      <router-outlet />
    </main>
  `,
  styles: [`
    mat-toolbar {
      position: sticky;
      top: 0;
      z-index: 100;
    }

    .brand {
      font-weight: 500;
      margin-right: 1rem;
    }

    .nav-links a.active {
      border-bottom: 2px solid currentColor;
      border-radius: 0;
    }

    .spacer {
      flex: 1;
    }

    .shell-content {
      max-width: 1200px;
      margin: 1.5rem auto;
      padding: 0 1rem;
    }
  `],
})
export class ShellComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly email = this.authService.email;

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
