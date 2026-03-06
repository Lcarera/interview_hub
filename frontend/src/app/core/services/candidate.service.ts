import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Candidate, CandidateRequest } from '../models/candidate.model';

@Injectable({ providedIn: 'root' })
export class CandidateService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/candidates`;

  list(): Observable<Candidate[]> {
    return this.http.get<Candidate[]>(this.baseUrl);
  }

  get(id: string): Observable<Candidate> {
    return this.http.get<Candidate>(`${this.baseUrl}/${id}`);
  }

  create(body: CandidateRequest): Observable<Candidate> {
    return this.http.post<Candidate>(this.baseUrl, body);
  }

  update(id: string, body: CandidateRequest): Observable<Candidate> {
    return this.http.put<Candidate>(`${this.baseUrl}/${id}`, body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
