import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../environments/environment';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  message: string;
  accessToken: string;  // corrigé
}

export interface RegisterRequest {
  username: string;
  password: string;
  role: string;
}

export interface RegisterResponse {
  accessToken: string;  // corrigé
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'token';
  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // ── POST /auth/login ──────────────────────────────────────────
  login(username: string, password: string): Observable<LoginResponse> {
    const body: LoginRequest = { username, password };
    return this.http
      .post<LoginResponse>(`${this.apiUrl}/auth/login`, body)
      .pipe(
        tap((res) => localStorage.setItem(this.TOKEN_KEY, res.accessToken))
      );
  }

  // ── POST /auth/register ───────────────────────────────────────
  register(username: string, password: string, role: string): Observable<RegisterResponse> {
    const body: RegisterRequest = { username, password, role };
    return this.http
      .post<RegisterResponse>(`${this.apiUrl}/auth/register`, body)
      .pipe(
        tap((res) => localStorage.setItem(this.TOKEN_KEY, res.accessToken))
      );
  }

  // ── Session helpers ───────────────────────────────────────────
  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  }

  getUsername(): string | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.sub ?? null;
    } catch {
      return null;
    }
  }
}