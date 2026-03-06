import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Profile } from '../models/profile.model';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/profiles`;

  list(): Observable<Profile[]> {
    return this.http.get<Profile[]>(this.baseUrl);
  }

  getMe(): Observable<Profile> {
    return this.http.get<Profile>(`${this.baseUrl}/me`);
  }
}
