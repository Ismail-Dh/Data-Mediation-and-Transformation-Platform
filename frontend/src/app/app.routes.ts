import { Routes } from '@angular/router';
import { authGuard } from './guards/auth-guard';
import { Login } from './pages/login/login';
import { Register } from './pages/register/register.component';
import { Dashboard } from './pages/dashboard/dashboard';
import { adminGuard } from './guards/role.guard';
import { UsersComponent } from './pages/users/users.component';
import { ForgotPassword } from './pages/forgot-password/forgot-password';
export const routes: Routes = [
  { path: 'login',     component: Login },
  { path: 'forgot-password', component: ForgotPassword },
  { path: 'register',  component: Register },
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard] },
  { path: 'users',     component: UsersComponent,  canActivate: [authGuard, adminGuard] },
  { path: '',          redirectTo: 'login', pathMatch: 'full' },
];