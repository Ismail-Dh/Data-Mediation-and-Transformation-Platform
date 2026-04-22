import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';

import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';

import { UserService } from '../../services/users/user.service';
import { User, CreateUserRequest ,UpdateUserRequest} from '../../models/user';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatTableModule, MatButtonModule, MatIconModule,
    MatDialogModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatSnackBarModule, MatProgressSpinnerModule,
    MatTooltipModule
  ],
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss'
})
export class UsersComponent implements OnInit {
  private fb          = inject(FormBuilder);
  private userService = inject(UserService);
  private snack       = inject(MatSnackBar);

  users            = signal<User[]>([]);
  loading          = signal(true);
  showForm         = signal(false);
  editingUser      = signal<User | null>(null);
  displayedColumns = ['id', 'username', 'role', 'actions'];

  form = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.required, Validators.minLength(6)]],
    role:     ['DEVELOPER', Validators.required]
  });

  ngOnInit() { this.loadUsers(); }

  loadUsers() {
    this.loading.set(true);
    this.userService.getUsers().subscribe({
      next:  (data) => { this.users.set(data); this.loading.set(false); },
      error: ()     => { this.loading.set(false); this.notify('Erreur chargement'); }
    });
  }

  openCreate() {
    this.editingUser.set(null);
    this.form.reset({ role: 'DEVELOPER' });
    this.form.get('password')?.setValidators([Validators.required, Validators.minLength(6)]);
    this.form.get('password')?.updateValueAndValidity();
    this.showForm.set(true);
  }

  openEdit(user: User) {
    this.editingUser.set(user);
    this.form.patchValue({ username: user.username, role: user.role, password: '' });
    this.form.get('password')?.clearValidators();
    this.form.get('password')?.updateValueAndValidity();
    this.showForm.set(true);
  }

save() {
  if (this.form.invalid) return;
  const val = this.form.value;
  const editing = this.editingUser();

  if (editing) {
    const updateData: UpdateUserRequest = {
      username: val.username!,
      role: val.role as any
    };
    this.userService.updateUser(editing.id, updateData).subscribe({
      next: () => { this.notify('Utilisateur modifié '); this.showForm.set(false); this.loadUsers(); },
      error: (err) => this.handleError(err)
    });
  } else {
    this.userService.createUser(val as CreateUserRequest).subscribe({
      next: () => { this.notify('Utilisateur créé '); this.showForm.set(false); this.loadUsers(); },
      error: (err) => this.handleError(err)
    });
  }
}

  delete(user: User) {
  if (!confirm(`Supprimer "${user.username}" ?`)) return;
  this.userService.deleteUser(user.id).subscribe({
    next: () => { this.notify('Utilisateur supprimé ✅'); this.loadUsers(); },
    error: (err) => this.handleError(err)
  });
  }

  private handleError(err: any) {
  const status = err.status;
  const body   = err.error;

  if (status === 400) {
    // Erreurs de validation par champ : { username: "...", password: "..." }
    const messages = Object.values(body).join(' | ');
    this.notify(`Validation : ${messages}`, true);

  } else if (status === 404) {
    this.notify(`Introuvable : ${body.error}`, true);

  } else if (status === 409) {
    this.notify(`Conflit : ce nom d'utilisateur existe déjà`, true);

  } else {
    this.notify('Erreur serveur interne', true);
  }
}

  cancel() { this.showForm.set(false); }

  private notify(msg: string, isError = false) {
  this.snack.open(msg, 'OK', {
    duration: 4000,
    panelClass: isError ? ['snack-error'] : ['snack-success']
  });
}
}