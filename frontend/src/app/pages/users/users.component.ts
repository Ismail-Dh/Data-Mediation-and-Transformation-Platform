import { Component, OnInit, signal, inject, computed, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { UserService } from '../../services/users/user.service';
import { User, CreateUserRequest, UpdateUserRequest } from '../../models/user';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, MatSnackBarModule],
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss'
})
export class UsersComponent implements OnInit {
  private fb          = inject(FormBuilder);
  private userService = inject(UserService);
  private snack       = inject(MatSnackBar);
  private cdr         = inject(ChangeDetectorRef);

  users         = signal<User[]>([]);
  loading       = signal(true);
  showForm      = signal(false);
  editingUser   = signal<User | null>(null);
  showResetForm = signal<User | null>(null);

  // ✅ signals au lieu de simples propriétés
  searchQuery = signal('');
  filterRole  = signal('');

  filtered = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    return this.users().filter(u => {
      const matchQ    = !q || u.username.toLowerCase().includes(q) || u.role.toLowerCase().includes(q);
      const matchRole = !this.filterRole() || u.role === this.filterRole();
      return matchQ && matchRole;
    });
  });

  form = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.required, Validators.minLength(6)]],
    role:     ['DEVELOPER', Validators.required]
  });

  resetForm = this.fb.group({
    newPassword: ['', [Validators.required, Validators.minLength(8)]]
  });

  ngOnInit() { this.loadUsers(); }

  loadUsers() {
    this.loading.set(true);
    this.userService.getUsers().subscribe({
      next: data => {
        this.users.set(data);
        this.loading.set(false);
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading.set(false);
        this.notify('Error loading users');
        this.cdr.detectChanges();
      }
    });
  }

  countByRole(role: string): number {
    return this.users().filter(u => u.role === role).length;
  }

  // ✅ met à jour les signals
  applyFilter(field: 'search' | 'role', value: string) {
    if (field === 'search') this.searchQuery.set(value);
    else                    this.filterRole.set(value);
    this.cdr.detectChanges();
  }

  openCreate() {
    this.editingUser.set(null);
    this.form.reset({ role: 'DEVELOPER' });
    this.form.get('password')?.setValidators([Validators.required, Validators.minLength(6)]);
    this.form.get('password')?.updateValueAndValidity();
    this.showForm.set(true);
    this.cdr.detectChanges();
  }

  openEdit(user: User) {
    this.editingUser.set(user);
    this.form.patchValue({ username: user.username, role: user.role, password: '' });
    this.form.get('password')?.clearValidators();
    this.form.get('password')?.updateValueAndValidity();
    this.showForm.set(true);
    this.cdr.detectChanges();
  }

  save() {
    if (this.form.invalid) return;
    const val     = this.form.value;
    const editing = this.editingUser();

    if (editing) {
      const updateData: UpdateUserRequest = { username: val.username!, role: val.role as any };
      this.userService.updateUser(editing.id, updateData).subscribe({
        next: () => { this.notify('User updated'); this.showForm.set(false); this.loadUsers(); this.cdr.detectChanges(); },
        error: err => this.handleError(err)
      });
    } else {
      this.userService.createUser(val as CreateUserRequest).subscribe({
        next: () => { this.notify('User created'); this.showForm.set(false); this.loadUsers(); this.cdr.detectChanges(); },
        error: err => this.handleError(err)
      });
    }
  }

  delete(user: User) {
    if (!confirm(`Delete "${user.username}"?`)) return;
    this.userService.deleteUser(user.id).subscribe({
      next: () => { this.notify('User deleted'); this.loadUsers(); this.cdr.detectChanges(); },
      error: err => this.handleError(err)
    });
  }

  cancel() { this.showForm.set(false); this.cdr.detectChanges(); }

  openReset(user: User) { this.showResetForm.set(user); this.resetForm.reset(); this.cdr.detectChanges(); }

  resetPassword() {
    if (this.resetForm.invalid) return;
    const user = this.showResetForm();
    if (!user) return;
    this.userService.resetPassword(user.id, this.resetForm.value.newPassword!).subscribe({
      next: () => { this.notify('Password changed'); this.showResetForm.set(null); this.cdr.detectChanges(); },
      error: err => this.handleError(err)
    });
  }

  private handleError(err: any) {
    const status = err.status;
    const body   = err.error;
    if      (status === 400) this.notify(`Validation: ${Object.values(body).join(' | ')}`, true);
    else if (status === 404) this.notify(`Not found: ${body.error}`, true);
    else if (status === 409) this.notify('Conflict: username already exists', true);
    else                     this.notify('Internal server error', true);
    this.cdr.detectChanges();
  }

  private notify(msg: string, isError = false) {
    this.snack.open(msg, 'OK', { duration: 4000, panelClass: isError ? ['snack-error'] : ['snack-success'] });
  }
}