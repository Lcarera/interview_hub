import { Injectable, signal } from '@angular/core';
import { environment } from '../../../environments/environment';

const TOKEN_KEY = 'ih_token';
const EMAIL_KEY = 'ih_email';
const EXPIRES_AT_KEY = 'ih_expires_at';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly email = signal<string | null>(localStorage.getItem(EMAIL_KEY));

  login(): void {
    window.location.href = `${environment.apiUrl}/auth/google`;
  }

  handleCallback(token: string, email: string, expiresIn: number): void {
    const expiresAt = Date.now() + expiresIn * 1000;
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(EMAIL_KEY, email);
    localStorage.setItem(EXPIRES_AT_KEY, String(expiresAt));
    this.email.set(email);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    const expiresAt = Number(localStorage.getItem(EXPIRES_AT_KEY));
    return !!token && Date.now() < expiresAt;
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EMAIL_KEY);
    localStorage.removeItem(EXPIRES_AT_KEY);
    this.email.set(null);
  }
}
