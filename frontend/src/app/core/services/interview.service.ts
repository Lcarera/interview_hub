import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Interview } from '../models/interview.model';

@Injectable({ providedIn: 'root' })
export class InterviewService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/interviews`;

  list(page = 0, size = 20): Observable<unknown> {
    return this.http.get(this.base, { params: { page, size } });
  }

  get(id: string): Observable<Interview> {
    return this.http.get<Interview>(`${this.base}/${id}`);
  }

  create(body: Partial<Interview>): Observable<Interview> {
    return this.http.post<Interview>(this.base, body);
  }

  update(id: string, body: Partial<Interview>): Observable<Interview> {
    return this.http.put<Interview>(`${this.base}/${id}`, body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
