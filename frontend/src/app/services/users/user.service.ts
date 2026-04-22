import { Injectable } from '@angular/core';
import { HttpClient ,HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User, CreateUserRequest, UpdateUserRequest } from '../../models/user';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly apiUrl = `${environment.apiUrl}/api/users`;

  constructor(private http: HttpClient) {}

  getUsers(): Observable<User[]> {
    return this.http.get<User[]>(this.apiUrl);
  }

  createUser(data: CreateUserRequest): Observable<User> {
  return this.http.post<User>(`${this.apiUrl}/add`, data);
  }

  updateUser(id: number, data: UpdateUserRequest): Observable<User> {
    return this.http.patch<User>(`${this.apiUrl}/${id}`, data);
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  updateRole(id: number, role: string): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/${id}/role`, { role });
  }
  resetPassword(id: number, newPassword: string): Observable<User> {
  return this.http.patch<User>(`${this.apiUrl}/${id}/reset-password`, { newPassword });
  }

   /**
   * Étape 1 — Envoyer le code par email
   */
  forgotPassword(username: string): Observable<string> {
    const params = new HttpParams().set('username', username);
    return this.http.post(`${this.apiUrl}/forgot-password`, null, {
      params,
      responseType: 'text'
    });
  }
 
  /**
   * Étape 2 — Vérifier le code
   */
  verifyCode(username: string, code: string): Observable<boolean> {
    const params = new HttpParams()
      .set('username', username)
      .set('code', code);
    return this.http.post<boolean>(`${this.apiUrl}/verify-code`, null, { params });
  }
 
  /**
   * Étape 3 — Réinitialiser le mot de passe
   */
  resetPasswordByCode(username: string, newPassword: string): Observable<string> {
    const params = new HttpParams()
      .set('username', username)
      .set('newPassword', newPassword);
    return this.http.post(`${this.apiUrl}/reset-password`, null, {
      params,
      responseType: 'text'
    });
  }
}