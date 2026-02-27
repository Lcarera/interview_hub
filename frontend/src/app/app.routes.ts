import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then(m => m.LoginComponent),
  },
  {
    path: 'auth/callback',
    loadComponent: () => import('./features/auth/callback/auth-callback').then(m => m.AuthCallbackComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell').then(m => m.ShellComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/dashboard/dashboard').then(m => m.DashboardComponent),
      },
      {
        path: 'interviews',
        loadComponent: () => import('./features/interviews/interview-list/interview-list').then(m => m.InterviewListComponent),
      },
      {
        path: 'interviews/:id',
        loadComponent: () => import('./features/interviews/interview-detail/interview-detail').then(m => m.InterviewDetailComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
