import { Routes } from '@angular/router';
import { authGuard } from './guards/auth-guard';

import { Login } from './pages/login/login';
import { Dashboard } from './pages/dashboard/dashboard';


export const routes: Routes = [
      { path: 'login', component: Login },
      { path:'dashboard',component: Dashboard, canActivate: [authGuard]},
      { path: '', redirectTo: 'login', pathMatch: 'full' }

];
