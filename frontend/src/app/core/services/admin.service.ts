import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Profile } from '../models/profile.model';
import { environment } from '../../../environments/environment';

export interface CreateUserRequest {
  email: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/admin/users`;

  listUsers(page = 0, size = 20): Observable<{ content: Profile[]; totalElements: number }> {
    return this.http.get<{ content: Profile[]; totalElements: number }>(
      this.baseUrl, { params: { page: page.toString(), size: size.toString() } }
    );
  }

  createUser(request: CreateUserRequest): Observable<Profile> {
    return this.http.post<Profile>(this.baseUrl, request);
  }

  updateRole(id: string, role: string): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/${id}/role`, { role });
  }

  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
