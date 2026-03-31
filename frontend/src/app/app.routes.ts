import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then(m => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register').then(m => m.RegisterComponent),
  },
  {
    path: 'auth/callback',
    loadComponent: () => import('./features/auth/callback/auth-callback').then(m => m.AuthCallbackComponent),
  },
  {
    path: 'auth/verify',
    loadComponent: () => import('./features/auth/verify/verify').then(m => m.VerifyComponent),
  },
  {
    path: 'auth/forgot-password',
    loadComponent: () => import('./features/auth/forgot-password/forgot-password').then(m => m.ForgotPasswordComponent),
  },
  {
    path: 'auth/reset-password',
    loadComponent: () => import('./features/auth/reset-password/reset-password').then(m => m.ResetPasswordComponent),
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
      {
        path: 'candidates',
        loadComponent: () => import('./features/candidates/candidate-list/candidate-list').then(m => m.CandidateListComponent),
      },
      {
        path: 'calendar',
        loadComponent: () => import('./features/calendar/calendar').then(m => m.CalendarComponent),
      },
      {
        path: 'admin/users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/admin/user-management/user-management').then(m => m.UserManagementComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
