import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ShadowingRequest } from '../models/shadowing-request.model';

@Injectable({ providedIn: 'root' })
export class ShadowingRequestService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/shadowing-requests`;

  list(page = 0, size = 20): Observable<unknown> {
    return this.http.get(this.base, { params: { page, size } });
  }

  get(id: string): Observable<ShadowingRequest> {
    return this.http.get<ShadowingRequest>(`${this.base}/${id}`);
  }

  create(body: Partial<ShadowingRequest>): Observable<ShadowingRequest> {
    return this.http.post<ShadowingRequest>(this.base, body);
  }

  updateStatus(id: string, status: string): Observable<ShadowingRequest> {
    return this.http.patch<ShadowingRequest>(`${this.base}/${id}/status`, { status });
  }
}
