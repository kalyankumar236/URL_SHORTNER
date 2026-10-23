import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from './config';
import {
  AgentsResponse,
  AnalyticsSummary,
  Approval,
  ApprovalDecisionRequest,
  CreateLinkRequest,
  CreateWorkflowRequest,
  GateResult,
  Link,
  LinkAnalytics,
  Metrics,
  Page,
  PoliciesResponse,
  ReplanRequest,
  Task,
  Workflow,
  WorkflowEvent,
} from './models';

/** Thin typed wrapper over the backend REST API. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = `${API_BASE}/api/v1`;

  // ---- URL shortener ----

  createLink(body: CreateLinkRequest): Observable<Link> {
    return this.http.post<Link>(`${this.base}/links`, body);
  }

  listLinks(page = 0, size = 20, sort = 'createdAt', direction = 'DESC'): Observable<Page<Link>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort)
      .set('direction', direction);
    return this.http.get<Page<Link>>(`${this.base}/links`, { params });
  }

  getLink(code: string): Observable<Link> {
    return this.http.get<Link>(`${this.base}/links/${code}`);
  }

  deleteLink(code: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/links/${code}`);
  }

  linkAnalytics(code: string, from?: string, to?: string): Observable<LinkAnalytics> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<LinkAnalytics>(`${this.base}/links/${code}/analytics`, { params });
  }

  analyticsSummary(from?: string, to?: string, topN = 5): Observable<AnalyticsSummary> {
    let params = new HttpParams().set('topN', topN);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<AnalyticsSummary>(`${this.base}/analytics/summary`, { params });
  }

  // ---- Orchestration ----

  createWorkflow(body: CreateWorkflowRequest): Observable<Workflow> {
    return this.http.post<Workflow>(`${this.base}/workflows`, body);
  }

  listWorkflows(page = 0, size = 20): Observable<Page<Workflow>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Workflow>>(`${this.base}/workflows`, { params });
  }

  getWorkflow(id: string): Observable<Workflow> {
    return this.http.get<Workflow>(`${this.base}/workflows/${id}`);
  }

  workflowTasks(id: string): Observable<Task[]> {
    return this.http.get<Task[]>(`${this.base}/workflows/${id}/tasks`);
  }

  workflowEvents(id: string): Observable<WorkflowEvent[]> {
    return this.http.get<WorkflowEvent[]>(`${this.base}/workflows/${id}/events`);
  }

  workflowGates(id: string): Observable<GateResult[]> {
    return this.http.get<GateResult[]>(`${this.base}/workflows/${id}/gates`);
  }

  runWorkflow(id: string): Observable<Workflow> {
    return this.http.post<Workflow>(`${this.base}/workflows/${id}/run`, {});
  }

  cancelWorkflow(id: string): Observable<Workflow> {
    return this.http.post<Workflow>(`${this.base}/workflows/${id}/cancel`, {});
  }

  replanWorkflow(id: string, body: ReplanRequest): Observable<Workflow> {
    return this.http.post<Workflow>(`${this.base}/workflows/${id}/replan`, body);
  }

  // ---- Approvals ----

  listApprovals(): Observable<Approval[]> {
    return this.http.get<Approval[]>(`${this.base}/approvals`);
  }

  getApproval(id: string): Observable<Approval> {
    return this.http.get<Approval>(`${this.base}/approvals/${id}`);
  }

  decideApproval(id: string, body: ApprovalDecisionRequest): Observable<Approval> {
    return this.http.post<Approval>(`${this.base}/approvals/${id}/decision`, body);
  }

  // ---- Platform ----

  metrics(): Observable<Metrics> {
    return this.http.get<Metrics>(`${this.base}/metrics`);
  }

  agents(): Observable<AgentsResponse> {
    return this.http.get<AgentsResponse>(`${this.base}/agents`);
  }

  policies(): Observable<PoliciesResponse> {
    return this.http.get<PoliciesResponse>(`${this.base}/policies`);
  }
}
