import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth/auth-service';

export const adminGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);

  const token = auth.getToken();
  if (!token) { router.navigate(['/login']); return false; }

  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    const role = payload.role ?? payload.roles?.[0];
    if (role === 'ROLE_ADMIN') return true;
  } catch {}

  router.navigate(['/dashboard']);
  return false;
};