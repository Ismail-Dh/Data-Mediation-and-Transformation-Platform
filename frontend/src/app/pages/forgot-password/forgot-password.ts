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
            this.errorMessage = 'Accès refusé — vérifiez la configuration Spring Security.';
          } else if (err.status === 404) {
            this.errorMessage = 'Aucun utilisateur trouvé avec ce nom.';
          } else {
            this.errorMessage = err.error?.message || err.message || 'Erreur lors de l\'envoi du code.';
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
            this.errorMessage = 'Code incorrect ou expiré. Veuillez réessayer.';
          }
          this.cdr.detectChanges();
        },
        error: () => {
          this.errorMessage = 'Erreur lors de la vérification.';
          this.cdr.detectChanges();
        }
      });
  }
 
  // ── Step 3 ──────────────────────────────────────────────────────────────
 
  resetPassword(): void {
    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage = 'Les mots de passe ne correspondent pas.';
      return;
    }
    if (this.newPassword.length < 6) {
      this.errorMessage = 'Le mot de passe doit contenir au moins 6 caractères.';
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
          this.errorMessage = 'Erreur lors de la réinitialisation.';
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
