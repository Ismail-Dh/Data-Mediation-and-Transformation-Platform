import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
@Component({
  selector: 'app-login',
  imports: [
    CommonModule,
    ReactiveFormsModule
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
loginForm: FormGroup;
  isLoading = false;
  errorMessage = '';
  showPassword = false;
 
  constructor(
    private fb: FormBuilder,
    private router: Router
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
    });
  }
 
  get email() {
    return this.loginForm.get('email')!;
  }
 
  get password() {
    return this.loginForm.get('password')!;
  }
 
  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }
 
  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }
 
    this.isLoading = true;
    this.errorMessage = '';
 
    const { email, password } = this.loginForm.value;
 
   /* this.authService.login(email, password).subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate(['/pipelines']);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage =
          err?.error?.message || 'Invalid credentials. Please try again.';
      },
    });*/
  }
}
