import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AnalyticsSummary, Link, LinkAnalytics } from '../../core/models';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-links',
  imports: [DatePipe, ReactiveFormsModule, StatusBadge],
  templateUrl: './links.html',
})
export class Links {
  private fb = inject(FormBuilder);
  private api = inject(ApiService);

  protected readonly links = signal<Link[]>([]);
  protected readonly summary = signal<AnalyticsSummary | null>(null);
  protected readonly selected = signal<LinkAnalytics | null>(null);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly creating = signal(false);

  protected readonly form = this.fb.group({
    destinationUrl: ['', [Validators.required, Validators.pattern(/^https?:\/\/.+/i)]],
    alias: [''],
    expiresAt: [''],
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listLinks(0, 50).subscribe({
      next: (page) => {
        this.links.set(page.items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load links. Is the backend running on :8080?');
        this.loading.set(false);
      },
    });
    // Summary requires admin; silently ignore if unauthorized.
    this.api.analyticsSummary(undefined, undefined, 5).subscribe({
      next: (s) => this.summary.set(s),
      error: () => this.summary.set(null),
    });
  }

  protected create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.creating.set(true);
    this.notice.set(null);
    this.error.set(null);

    const v = this.form.value;
    this.api
      .createLink({
        destinationUrl: v.destinationUrl!.trim(),
        alias: v.alias?.trim() || null,
        expiresAt: v.expiresAt ? new Date(v.expiresAt).toISOString() : null,
      })
      .subscribe({
        next: (link) => {
          this.creating.set(false);
          this.notice.set(`Created ${link.shortUrl}`);
          this.form.reset();
          this.load();
        },
        error: (e) => {
          this.creating.set(false);
          this.error.set(
            e?.status === 409
              ? 'That alias is already taken. Choose another.'
              : e?.status === 400
                ? 'Invalid URL or alias. Please check your input.'
                : 'Failed to create link.',
          );
        },
      });
  }

  protected remove(link: Link): void {
    this.api.deleteLink(link.shortCode).subscribe({
      next: () => {
        this.notice.set(`Deactivated /${link.shortCode}`);
        if (this.selected()?.shortCode === link.shortCode) this.selected.set(null);
        this.load();
      },
      error: (e) => {
        this.error.set(
          e?.status === 401 || e?.status === 403
            ? 'Not authorized. Set admin credentials in the header (demo: admin/admin).'
            : 'Failed to deactivate link.',
        );
      },
    });
  }

  protected viewAnalytics(link: Link): void {
    this.error.set(null);
    this.api.linkAnalytics(link.shortCode).subscribe({
      next: (a) => this.selected.set(a),
      error: (e) => {
        this.selected.set(null);
        this.error.set(
          e?.status === 401 || e?.status === 403
            ? 'Analytics require admin credentials (demo: admin/admin).'
            : 'Could not load analytics.',
        );
      },
    });
  }

  protected dailyRows(a: LinkAnalytics): { day: string; clicks: number }[] {
    return Object.entries(a.dailyClicks)
      .map(([day, clicks]) => ({ day, clicks }))
      .sort((x, y) => x.day.localeCompare(y.day));
  }
}
