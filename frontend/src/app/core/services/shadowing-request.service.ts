import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ShadowingRequest } from '../models/shadowing-request.model';

@Injectable({ providedIn: 'root' })
export class ShadowingRequestService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  requestShadowing(interviewId: string): Observable<ShadowingRequest> {
    return this.http.post<ShadowingRequest>(
      `${this.apiUrl}/api/interviews/${interviewId}/shadowing-requests`,
      {}
    );
  }

  approve(id: string): Observable<ShadowingRequest> {
    return this.http.post<ShadowingRequest>(
      `${this.apiUrl}/api/shadowing-requests/${id}/approve`,
      {}
    );
  }

  reject(id: string, reason: string): Observable<ShadowingRequest> {
    return this.http.post<ShadowingRequest>(
      `${this.apiUrl}/api/shadowing-requests/${id}/reject`,
      { reason }
    );
  }

  cancel(id: string): Observable<ShadowingRequest> {
    return this.http.post<ShadowingRequest>(
      `${this.apiUrl}/api/shadowing-requests/${id}/cancel`,
      {}
    );
  }
}
