import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth-service';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule,RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class Register {
  registerForm: FormGroup;
  isLoading = false;
  errorMessage = '';
  showPassword = false;
 
  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.registerForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      role: ['DEVELOPER', Validators.required],
    });
  }
 
  get username() { return this.registerForm.get('username')!; }
  get password() { return this.registerForm.get('password')!; }
  get role()     { return this.registerForm.get('role')!; }
 
  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }
 
  onSubmit(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }
 
    this.isLoading = true;
    this.errorMessage = '';
    const { username, password, role } = this.registerForm.value;
 
    this.authService.register(username, password, role).subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.isLoading = false;
        if (err?.status === 409) {
          this.errorMessage = "Ce nom d'utilisateur est déjà pris.";
        } else {
          this.errorMessage = err?.error?.message || "Erreur lors de l'inscription.";
        }
      },
    });
  }
}