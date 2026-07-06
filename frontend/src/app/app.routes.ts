import { Routes } from '@angular/router';
import { authGuard } from './guards/auth-guard';
import { Login } from './pages/login/login';
import { Register } from './pages/register/register.component';
import { Dashboard } from './pages/dashboard/dashboard';
import { adminGuard } from './guards/role.guard';
import { UsersComponent } from './pages/users/users.component';
import { ForgotPassword } from './pages/forgot-password/forgot-password';
//import { PipelineAdminComponent } from './pages/pipeline-admin-component/pipeline-admin-component';
import { PipelineDeveloperComponent } from './pages/pipeline-developer-component/pipeline-developer-component';
import { ProviderListComponent } from './pages/provider-list-component/provider-list-component';
import { DashboardContent } from './pages/dashboard-content/dashboard-content';
import { ValidationRulesComponent } from './pages/validation-rules/validation-rules.component';
import { AdminTemplate } from './pages/admin-template/admin-template';
import { DeveloperTemplate } from './pages/developer-template/developer-template';
import { AuditLogAdminComponent } from './pages/audit-log-admin/audit-log-admin.component';
import { AuditLogMeComponent } from './pages/audit-log-me/audit-log-me.component';
import { MonitoringComponent } from './pages/monitoring/monitoring.component';
// ── NEW ──────────────────────────────────────────────────────────────────────
import { RegistryComponent } from './pages/registry-component/registry-component';

export const routes: Routes = [
  { path: 'login',           component: Login },
  { path: 'forgot-password', component: ForgotPassword },
  { path: 'register',        component: Register },
  {
    path: '',
    component: Dashboard,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard',           component: DashboardContent,           canActivate: [authGuard]  },
      { path: 'users',               component: UsersComponent,             canActivate: [adminGuard] },
      // { path: 'admin/pipelines',     component: PipelineAdminComponent,     canActivate: [adminGuard] },
      { path: 'validationRules',     component: ValidationRulesComponent,   canActivate: [adminGuard] },
      { path: 'developer/pipelines', component: PipelineDeveloperComponent, canActivate: [authGuard]  },
      { path: 'provider',            component: ProviderListComponent,      canActivate: [authGuard]  },
      { path: 'admin/templates',     component: AdminTemplate,              canActivate: [adminGuard] },
      { path: 'admin/audit-logs',    component: AuditLogAdminComponent,     canActivate: [adminGuard] },
      { path: 'my-logs',             component: AuditLogMeComponent,        canActivate: [authGuard]  },
      { path: 'developer/templates', component: DeveloperTemplate,          canActivate: [authGuard]  },
      { path: 'admin/monitoring',    component: MonitoringComponent,        canActivate: [adminGuard] },
      // ── NEW ──────────────────────────────────────────────────────────────
      { path: 'developer/registries',    component: RegistryComponent,          canActivate: [authGuard] },
      // ─────────────────────────────────────────────────────────────────────
      { path: '',                    redirectTo: 'dashboard', pathMatch: 'full' },
    ]
  },
  { path: '**', redirectTo: 'login' },
];