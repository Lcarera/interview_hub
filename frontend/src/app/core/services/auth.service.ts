import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

const TOKEN_KEY = 'ih_token';
const EMAIL_KEY = 'ih_email';
const EXPIRES_AT_KEY = 'ih_expires_at';
const PROFILE_ID_KEY = 'ih_profile_id';

export interface AuthResponse {
  token: string;
  expiresIn: number;
  email: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  readonly email = signal<string | null>(localStorage.getItem(EMAIL_KEY));
  readonly profileId = signal<string | null>(localStorage.getItem(PROFILE_ID_KEY));

  loginWithGoogle(): void {
    window.location.href = `${environment.apiUrl}/auth/google`;
  }

  login(): void {
    this.loginWithGoogle();
  }

  register(email: string, password: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/auth/register`, { email, password });
  }

  loginWithEmail(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/login`, { email, password });
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/auth/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/auth/reset-password`, { token, newPassword });
  }

  verifyEmail(token: string): Observable<void> {
    return this.http.get<void>(`${environment.apiUrl}/auth/verify`, { params: { token } });
  }

  handleCallback(token: string, email: string, expiresIn: number): void {
    const expiresAt = Date.now() + expiresIn * 1000;
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(EMAIL_KEY, email);
    localStorage.setItem(EXPIRES_AT_KEY, String(expiresAt));
    this.email.set(email);

    const sub = this.parseJwtSubject(token);
    if (sub) {
      localStorage.setItem(PROFILE_ID_KEY, sub);
      this.profileId.set(sub);
    }
  }

  handleLoginResponse(response: AuthResponse): void {
    this.handleCallback(response.token, response.email, response.expiresIn);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    const expiresAt = Number(localStorage.getItem(EXPIRES_AT_KEY));
    return !!token && Date.now() < expiresAt;
  }

  getRole(): string | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = token.split('.')[1];
      const decoded = JSON.parse(atob(payload));
      return decoded.role ?? null;
    } catch {
      return null;
    }
  }

  isAdmin(): boolean {
    return this.getRole() === 'admin';
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EMAIL_KEY);
    localStorage.removeItem(EXPIRES_AT_KEY);
    localStorage.removeItem(PROFILE_ID_KEY);
    this.email.set(null);
    this.profileId.set(null);
  }

  private parseJwtSubject(token: string): string | null {
    try {
      const payload = token.split('.')[1];
      const decoded = JSON.parse(atob(payload));
      return decoded.sub ?? null;
    } catch {
      return null;
    }
  }
}
