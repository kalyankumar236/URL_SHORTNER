import { Injectable, signal } from '@angular/core';

/**
 * Holds the admin credentials used for Basic auth on protected (mutating/admin) endpoints.
 *
 * Credentials live in memory only — they are NEVER persisted to localStorage — so no secret
 * is written to disk by the browser. The backend is the real authority: it enforces
 * ROLE_ADMIN and separation of duties regardless of what the UI sends. Defaults to the
 * demo admin/admin so the prototype works immediately; change it via the header control.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly username = signal<string>('admin');
  private password = 'admin';

  setCredentials(username: string, password: string): void {
    this.username.set(username.trim());
    this.password = password;
  }

  /** Returns a Basic auth header value, or null when no username is configured. */
  authHeader(): string | null {
    const u = this.username();
    if (!u) {
      return null;
    }
    return 'Basic ' + btoa(`${u}:${this.password}`);
  }
}
