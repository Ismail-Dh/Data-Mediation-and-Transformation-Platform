import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, NgZone } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UserService } from '../../services/users/user.service';
import { Router } from '@angular/router';
import { finalize } from 'rxjs/internal/operators/finalize';




type Step = 'username' | 'code' | 'password' | 'success';

@Component({
  selector: 'app-forgot-password',
  imports: [CommonModule,FormsModule],
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.scss',
})
export class ForgotPassword {
step: Step = 'username';
 
  // Step 1
  username = '';
 
  // Step 2
  code = '';
 
  // Step 3
  newPassword = '';
  confirmPassword = '';
 
  loading = false;
  errorMessage = '';
 
  constructor(
    private userService: UserService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}
 
  // ── Step 1 ──────────────────────────────────────────────────────────────
 
  sendCode(): void {
    if (!this.username.trim()) return;
    this.loading = true;
    this.errorMessage = '';
 
    this.userService.forgotPassword(this.username.trim())
      .pipe(finalize(() => { this.loading = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: () => {
          this.step = 'code';
          this.cdr.detectChanges();
        },
        error: (err) => {
          if (err.status === 403) {
            this.errorMessage = 'Access denied — check the Spring Security configuration';
          } else if (err.status === 404) {
            this.errorMessage = 'No user found with this name.';
          } else {
            this.errorMessage = err.error?.message || err.message || 'Error sending the code.';
          }
          this.cdr.detectChanges();
        }
      });
  }
 
  // ── Step 2 ──────────────────────────────────────────────────────────────
 
  verifyCode(): void {
    if (!this.code.trim()) return;
    this.loading = true;
    this.errorMessage = '';
 
    this.userService.verifyCode(this.username, this.code.trim())
      .pipe(finalize(() => { this.loading = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: (valid: boolean) => {
          if (valid) {
            this.step = 'password';
          } else {
            this.errorMessage = 'Incorrect or expired code. Please try again.';
          }
          this.cdr.detectChanges();
        },
        error: () => {
          this.errorMessage = 'Error during verification.';
          this.cdr.detectChanges();
        }
      });
  }
 
  // ── Step 3 ──────────────────────────────────────────────────────────────
 
  resetPassword(): void {
    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage = 'Passwords do not match.';
      return;
    }
    if (this.newPassword.length < 6) {
      this.errorMessage = 'Password must be at least 6 characters long.';
      return;
    }
 
    this.loading = true;
    this.errorMessage = '';
 
    this.userService.resetPasswordByCode(this.username, this.newPassword)
      .pipe(finalize(() => { this.loading = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: () => {
          this.step = 'success';
          this.cdr.detectChanges();
        },
        error: () => {
          this.errorMessage = 'Error during password reset.';
          this.cdr.detectChanges();
        }
      });
  }
 
  // ── Navigation ───────────────────────────────────────────────────────────
 
  goToLogin(): void {
    this.router.navigate(['/login']);
  }
 
  resendCode(): void {
    this.code = '';
    this.errorMessage = '';
    this.sendCode();
  }
}
