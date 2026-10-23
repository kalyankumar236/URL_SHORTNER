/** Typed views of the backend API responses (see backend DTOs). */

export interface Workflow {
  id: string;
  status: string;
  stage: string;
  planVersion: number;
  originalRequirement: string;
  normalizedRequirement: string | null;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Task {
  id: string;
  name: string;
  agentRole: string;
  status: string;
  risk: string;
  attempts: number;
  maxAttempts: number;
  planVersion: number;
  dependsOn: string[];
  errorDetail: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface WorkflowEvent {
  seq: number;
  type: string;
  taskId: string | null;
  payloadJson: string | null;
  actor: string | null;
  createdAt: string;
}

export interface GateResult {
  gateName: string;
  outcome: string;
  evidenceJson: string | null;
  createdAt: string;
}

export interface Approval {
  id: string;
  workflowId: string;
  taskId: string | null;
  actionSummary: string;
  risk: string;
  decision: string;
  requestedBy: string | null;
  reviewer: string | null;
  reason: string | null;
  requestedAt: string;
  decidedAt: string | null;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Link {
  shortCode: string;
  shortUrl: string;
  destinationUrl: string;
  status: string;
  customAlias: boolean;
  expiresAt: string | null;
  createdAt: string;
}

export interface LinkAnalytics {
  shortCode: string;
  totalClicks: number;
  clicksInRange: number;
  dailyClicks: Record<string, number>;
}

export interface AnalyticsSummary {
  totalClicks: number;
  clicksInRange: number;
  topLinks: { shortCode: string; clicks: number }[];
}

export interface ProviderInfo {
  name: string;
  model: string;
  mock: boolean;
}

export interface AgentsResponse {
  provider: ProviderInfo;
  agents: { role: string; permittedActions: string[] }[];
}

export interface PoliciesResponse {
  model: string;
  allowedActionsByRole: Record<string, string[]>;
  approvalRequiredActions: string[];
  allowedTools: string[];
}

/** Metrics are a nested, documented map computed by the backend from persisted state. */
export type Metrics = Record<string, any>;

export interface CreateLinkRequest {
  destinationUrl: string;
  alias?: string | null;
  expiresAt?: string | null;
}

export interface CreateWorkflowRequest {
  requirement: string;
  taskInputs?: Record<string, string>;
}

export interface ReplanRequest {
  fromTask?: string;
  reason?: string;
  taskInputs?: Record<string, string>;
}

export interface ApprovalDecisionRequest {
  approve: boolean;
  reason?: string;
}
