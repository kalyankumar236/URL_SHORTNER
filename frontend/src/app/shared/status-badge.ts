import { Component, computed, input } from '@angular/core';

/** Small colored pill for statuses, risk levels, outcomes and decisions. */
@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [style.background]="colors().bg" [style.color]="colors().fg">{{
    display()
  }}</span>`,
  styles: [
    `
      .badge {
        display: inline-block;
        padding: 0.15rem 0.55rem;
        border-radius: 999px;
        font-size: 0.74rem;
        font-weight: 700;
        letter-spacing: 0.02em;
        white-space: nowrap;
      }
    `,
  ],
})
export class StatusBadge {
  readonly value = input<string | null | undefined>('');

  protected display = computed(() => (this.value() ?? '').toString().replace(/_/g, ' ') || '—');

  protected colors = computed(() => palette(this.value()));
}

function palette(raw: string | null | undefined): { bg: string; fg: string } {
  const v = (raw ?? '').toString().toUpperCase();
  const green = { bg: '#dcfce7', fg: '#15803d' };
  const red = { bg: '#fee2e2', fg: '#b91c1c' };
  const amber = { bg: '#fef3c7', fg: '#b45309' };
  const blue = { bg: '#e0f2fe', fg: '#0369a1' };
  const indigo = { bg: '#e0e7ff', fg: '#4f46e5' };
  const gray = { bg: '#e2e8f0', fg: '#475569' };

  switch (v) {
    case 'SUCCEEDED':
    case 'COMPLETED':
    case 'PASS':
    case 'PASSED':
    case 'APPROVED':
    case 'ACTIVE':
    case 'LOW':
      return green;
    case 'FAILED':
    case 'FAIL':
    case 'CANCELLED':
    case 'CANCELED':
    case 'REJECTED':
    case 'ERROR':
    case 'EXPIRED':
    case 'HIGH':
      return red;
    case 'RUNNING':
    case 'IN_PROGRESS':
    case 'WAITING_FOR_APPROVAL':
    case 'PENDING':
    case 'REQUESTED':
    case 'MEDIUM':
      return amber;
    case 'READY':
    case 'WAITING':
    case 'BLOCKED':
      return blue;
    case 'STALE':
    case 'SAFE_STOP':
      return indigo;
    default:
      return gray;
  }
}
