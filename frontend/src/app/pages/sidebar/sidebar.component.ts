import { Component, signal } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth/auth-service';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatButtonModule, MatTooltipModule],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {
  isAdmin     = signal(false);
  isDeveloper = signal(false);
  sidebarOpen = signal(true);

  navItems = [
    { label: 'Dashboard',        icon: 'dashboard',      route: '/dashboard',           adminOnly: false, developerOnly: false },
    { label: 'Users',            icon: 'people',          route: '/users',               adminOnly: true,  developerOnly: false },
    { label: 'Providers',        icon: 'inventory_2',     route: '/provider',            adminOnly: true,  developerOnly: false },
    { label: 'Registries',       icon: 'registry',   route: 'developer/registries',    adminOnly: false,  developerOnly: true }, // ← NEW
    { label: 'Validation Rules', icon: 'rule',            route: '/validationRules',     adminOnly: true,  developerOnly: false },
    { label: 'Templates',        icon: 'description',     route: '/admin/templates',     adminOnly: true,  developerOnly: false },
    { label: 'Pipelines',        icon: 'build',           route: '/admin/pipelines',     adminOnly: true,  developerOnly: false },
    { label: 'My Pipelines',     icon: 'build',           route: '/developer/pipelines', adminOnly: false, developerOnly: true  },
    { label: 'Audit Logs',       icon: 'manage_search',   route: '/admin/audit-logs',    adminOnly: true,  developerOnly: false },
    { label: 'My Activity',      icon: 'history',         route: '/my-logs',             adminOnly: false, developerOnly: true  },
    { label: 'Monitoring',       icon: 'monitor_heart',   route: '/admin/monitoring',    adminOnly: true,  developerOnly: false },
  ];

  constructor(private authService: AuthService, private router: Router) {
    const token = this.authService.getToken();
    if (token) {
      const payload = JSON.parse(atob(token.split('.')[1]));
      this.isAdmin.set(payload.role === 'ROLE_ADMIN');
      this.isDeveloper.set(payload.role === 'ROLE_DEVELOPER');
    }
  }

  getVisibleItems() {
    return this.navItems.filter(item => {
      if (item.adminOnly     && !this.isAdmin())    return false;
      if (item.developerOnly && !this.isDeveloper()) return false;
      return true;
    });
  }

  toggleSidebar() {
    this.sidebarOpen.set(!this.sidebarOpen());
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}