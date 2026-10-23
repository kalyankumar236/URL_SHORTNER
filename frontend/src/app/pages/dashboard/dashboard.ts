import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Metrics, Workflow } from '../../core/models';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, DecimalPipe, RouterLink, StatusBadge],
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private api = inject(ApiService);

  protected readonly metrics = signal<Metrics | null>(null);
  protected readonly workflows = signal<Workflow[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api.metrics().subscribe({
      next: (m) => this.metrics.set(m),
      error: () => this.metrics.set(null),
    });

    this.api.listWorkflows(0, 25).subscribe({
      next: (page) => {
        this.workflows.set(page.items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load workflows. Is the backend running on :8080?');
        this.loading.set(false);
      },
    });
  }

  protected num(path: string): number {
    const m = this.metrics();
    if (!m) return 0;
    return path.split('.').reduce<any>((acc, k) => (acc == null ? acc : acc[k]), m) ?? 0;
  }
}
