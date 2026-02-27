import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Interview } from '../models/interview.model';
import { Page } from '../models/page.model';
import { CreateInterviewRequest, UpdateInterviewRequest } from '../models/dto.model';

@Injectable({ providedIn: 'root' })
export class InterviewService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/interviews`;

  list(page = 0, size = 20, sort?: string): Observable<Page<Interview>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (sort) {
      params = params.set('sort', sort);
    }
    return this.http.get<Page<Interview>>(this.base, { params });
  }

  get(id: string): Observable<Interview> {
    return this.http.get<Interview>(`${this.base}/${id}`);
  }

  create(body: CreateInterviewRequest): Observable<Interview> {
    return this.http.post<Interview>(this.base, body);
  }

  update(id: string, body: UpdateInterviewRequest): Observable<Interview> {
    return this.http.put<Interview>(`${this.base}/${id}`, body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
