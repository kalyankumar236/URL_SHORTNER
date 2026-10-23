import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ApiService } from './core/api.service';
import { AuthService } from './core/auth.service';
import { ProviderInfo } from './core/models';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private api = inject(ApiService);
  protected auth = inject(AuthService);

  protected readonly provider = signal<ProviderInfo | null>(null);
  protected readonly showCreds = signal(false);

  constructor() {
    this.api.agents().subscribe({
      next: (r) => this.provider.set(r.provider),
      error: () => this.provider.set(null),
    });
  }

  protected toggleCreds(): void {
    this.showCreds.update((v) => !v);
  }

  protected saveCreds(username: string, password: string): void {
    this.auth.setCredentials(username, password);
    this.showCreds.set(false);
  }
}
