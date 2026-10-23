import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Approval, GateResult, Task, Workflow, WorkflowEvent } from '../../core/models';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'app-workflow-detail',
  imports: [DatePipe, RouterLink, StatusBadge],
  templateUrl: './workflow-detail.html',
  styleUrl: './workflow-detail.css',
})
export class WorkflowDetail {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  protected readonly id = this.route.snapshot.paramMap.get('id')!;

  protected readonly workflow = signal<Workflow | null>(null);
  protected readonly tasks = signal<Task[]>([]);
  protected readonly events = signal<WorkflowEvent[]>([]);
  protected readonly gates = signal<GateResult[]>([]);
  protected readonly approvals = signal<Approval[]>([]);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly showReplan = signal(false);

  /** DAG laid out in topological columns using task dependency IDs. */
  protected readonly dagColumns = computed<Task[][]>(() => this.computeColumns(this.tasks()));

  protected readonly taskNames = computed(() => this.tasks().map((t) => t.name));

  /** A workflow is release-ready when it completed and every gate passed. */
  protected readonly releaseReady = computed(() => {
    const wf = this.workflow();
    if (!wf) return false;
    const gatesOk =
      this.gates().length > 0 &&
      this.gates().every((g) => ['PASS', 'PASSED'].includes(g.outcome.toUpperCase()));
    return wf.status.toUpperCase() === 'COMPLETED' && gatesOk;
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api.getWorkflow(this.id).subscribe({
      next: (wf) => {
        this.workflow.set(wf);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Workflow not found or backend unavailable.');
        this.loading.set(false);
      },
    });
    this.api.workflowTasks(this.id).subscribe({ next: (t) => this.tasks.set(t) });
    this.api.workflowEvents(this.id).subscribe({ next: (e) => this.events.set(e) });
    this.api.workflowGates(this.id).subscribe({ next: (g) => this.gates.set(g) });
    this.loadApprovals();
  }

  private loadApprovals(): void {
    this.api.listApprovals().subscribe({
      next: (list) => this.approvals.set(list.filter((a) => a.workflowId === this.id)),
      error: () => this.approvals.set([]),
    });
  }

  private act(op: () => void, message: string): void {
    this.busy.set(true);
    this.notice.set(null);
    this.error.set(null);
    void message;
    op();
  }

  protected run(): void {
    this.act(() => {
      this.api.runWorkflow(this.id).subscribe({
        next: () => this.afterAction('Workflow run triggered.'),
        error: (e) => this.failAction(e),
      });
    }, 'run');
  }

  protected cancel(): void {
    this.act(() => {
      this.api.cancelWorkflow(this.id).subscribe({
        next: () => this.afterAction('Workflow cancelled.'),
        error: (e) => this.failAction(e),
      });
    }, 'cancel');
  }

  protected replan(fromTask: string, reason: string): void {
    this.act(() => {
      this.api
        .replanWorkflow(this.id, {
          fromTask: fromTask || undefined,
          reason: reason || undefined,
        })
        .subscribe({
          next: () => {
            this.showReplan.set(false);
            this.afterAction(fromTask ? `Replanned from "${fromTask}".` : 'Full replan triggered.');
          },
          error: (e) => this.failAction(e),
        });
    }, 'replan');
  }

  protected decide(approval: Approval, approve: boolean): void {
    this.act(() => {
      this.api.decideApproval(approval.id, { approve }).subscribe({
        next: () => this.afterAction(approve ? 'Approved.' : 'Rejected.'),
        error: (e) => this.failAction(e),
      });
    }, 'decide');
  }

  private afterAction(msg: string): void {
    this.busy.set(false);
    this.notice.set(msg);
    this.load();
  }

  private failAction(e: { status?: number }): void {
    this.busy.set(false);
    this.error.set(
      e?.status === 401 || e?.status === 403
        ? 'Not authorized. Set admin credentials in the header (demo: admin/admin).'
        : 'Action failed. Please retry.',
    );
  }

  protected prettyJson(raw: string | null): string {
    if (!raw) return '';
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  }

  private computeColumns(tasks: Task[]): Task[][] {
    if (tasks.length === 0) return [];
    const byId = new Map(tasks.map((t) => [t.id, t]));
    const cache = new Map<string, number>();
    const level = (id: string, seen: Set<string>): number => {
      if (cache.has(id)) return cache.get(id)!;
      if (seen.has(id)) return 0; // cycle guard (DAG is acyclic, defensive only)
      const t = byId.get(id);
      if (!t || t.dependsOn.length === 0) {
        cache.set(id, 0);
        return 0;
      }
      seen.add(id);
      const l = 1 + Math.max(...t.dependsOn.map((d) => level(d, seen)));
      seen.delete(id);
      cache.set(id, l);
      return l;
    };
    const maxLevel = Math.max(0, ...tasks.map((t) => level(t.id, new Set())));
    const cols: Task[][] = Array.from({ length: maxLevel + 1 }, () => []);
    for (const t of tasks) {
      cols[level(t.id, new Set())].push(t);
    }
    return cols;
  }
}
