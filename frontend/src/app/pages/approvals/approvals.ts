import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Approval } from '../../core/models';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-approvals',
  imports: [DatePipe, RouterLink, StatusBadge],
  templateUrl: './approvals.html',
})
export class Approvals {
  private api = inject(ApiService);

  protected readonly approvals = signal<Approval[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busyId = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listApprovals().subscribe({
      next: (list) => {
        this.approvals.set(list);
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        this.error.set(
          e?.status === 401 || e?.status === 403
            ? 'Not authorized. Set admin credentials in the header (demo: admin/admin).'
            : 'Could not load approvals. Is the backend running?',
        );
      },
    });
  }

  protected decide(a: Approval, approve: boolean, reason: string): void {
    this.busyId.set(a.id);
    this.notice.set(null);
    this.error.set(null);
    this.api.decideApproval(a.id, { approve, reason: reason || undefined }).subscribe({
      next: () => {
        this.busyId.set(null);
        this.notice.set(`${approve ? 'Approved' : 'Rejected'}: ${a.actionSummary}`);
        this.load();
      },
      error: (e) => {
        this.busyId.set(null);
        this.error.set(
          e?.status === 401 || e?.status === 403
            ? 'Not authorized. Set admin credentials in the header.'
            : 'Decision failed. Please retry.',
        );
      },
    });
  }
}
