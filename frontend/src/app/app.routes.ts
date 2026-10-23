import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'intake',
    loadComponent: () => import('./pages/intake/intake').then((m) => m.Intake),
  },
  {
    path: 'workflows/:id',
    loadComponent: () =>
      import('./pages/workflow-detail/workflow-detail').then((m) => m.WorkflowDetail),
  },
  {
    path: 'approvals',
    loadComponent: () => import('./pages/approvals/approvals').then((m) => m.Approvals),
  },
  {
    path: 'links',
    loadComponent: () => import('./pages/links/links').then((m) => m.Links),
  },
  { path: '**', redirectTo: 'dashboard' },
];

