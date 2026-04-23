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
  isAdmin = signal(false);
  sidebarOpen = signal(true);

  navItems = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard', adminOnly: false },
    { label: 'Users', icon: 'people', route: '/users', adminOnly: true },
  ];

  constructor(private authService: AuthService, private router: Router) {
    const token = this.authService.getToken();
    if (token) {
      const payload = JSON.parse(atob(token.split('.')[1]));
      this.isAdmin.set(payload.role === 'ROLE_ADMIN');
    }
  }

  getVisibleItems() {
    return this.navItems.filter(item => !item.adminOnly || this.isAdmin());
  }

  toggleSidebar() {
    this.sidebarOpen.set(!this.sidebarOpen());
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}