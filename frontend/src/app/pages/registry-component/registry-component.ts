// app/pages/registry/registry.component.ts

import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators
} from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { RegistryService } from '../../services/registry/registry-service';
import { Registry } from '../../models/registry.model';

@Component({
  selector: 'app-registry',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './registry-component.html',
  styleUrl: './registry-component.scss'
})
export class RegistryComponent implements OnInit {

  registries: Registry[] = [];
  filtered: Registry[]   = [];
  searchQuery = '';

  showModal   = false;
  editingId: number | null = null;
  deletingRegistry: Registry | null = null;

  showPassword = false;
  saveError: string | null = null;

  form!: FormGroup;

  constructor(
    private registryService: RegistryService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.buildForm();
    this.load();
  }

  // ── Form ──────────────────────────────────────────────────────────────────

  private buildForm(): void {
    this.form = this.fb.group({
      name:     ['', Validators.required],
      url:      ['', Validators.required],
      username: ['', Validators.required],
      password: ['', Validators.required]   // required on create; relaxed on edit
    });
  }

  // ── Data ──────────────────────────────────────────────────────────────────

  private load(): void {
    this.registryService.getAll().subscribe({
      next: data => {
        this.registries = data;
        this.applyFilter();
        this.cdr.detectChanges();
      },
      error: () => this.cdr.detectChanges()
    });
  }

  applyFilter(): void {
    const q = this.searchQuery.trim().toLowerCase();
    this.filtered = q
      ? this.registries.filter(r =>
          r.name.toLowerCase().includes(q) ||
          r.url.toLowerCase().includes(q)  ||
          r.username.toLowerCase().includes(q)
        )
      : [...this.registries];
    this.cdr.detectChanges();
  }

  // ── Modal ─────────────────────────────────────────────────────────────────

  openCreate(): void {
    this.editingId   = null;
    this.saveError   = null;
    this.showPassword = false;
    this.form.reset();
    // password is required on create
    this.form.get('password')!.setValidators(Validators.required);
    this.form.get('password')!.updateValueAndValidity();
    this.showModal = true;
    this.cdr.detectChanges();
  }

  openEdit(r: Registry): void {
    this.editingId   = r.id;
    this.saveError   = null;
    this.showPassword = false;
    // password is optional on edit (leave blank to keep current)
    this.form.get('password')!.clearValidators();
    this.form.get('password')!.updateValueAndValidity();
    this.form.patchValue({
      name:     r.name,
      url:      r.url,
      username: r.username,
      password: ''
    });
    this.showModal = true;
    this.cdr.detectChanges();
  }

  closeModal(): void {
    this.showModal   = false;
    this.editingId   = null;
    this.saveError   = null;
    this.showPassword = false;
    this.cdr.detectChanges();
  }

  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }

  // ── Save ──────────────────────────────────────────────────────────────────

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saveError = null;
    const val = this.form.value;

    if (this.editingId !== null) {
      // Only send password if the user typed something
      const req: any = {
        name:     val.name     || undefined,
        url:      val.url      || undefined,
        username: val.username || undefined,
      };
      if (val.password) req.password = val.password;

      this.registryService.update(this.editingId, req).subscribe({
        next: () => { this.load(); this.closeModal(); },
        error: err => { this.saveError = err?.error?.message ?? 'Update failed.'; this.cdr.detectChanges(); }
      });
    } else {
      this.registryService.create(val).subscribe({
        next: () => { this.load(); this.closeModal(); },
        error: err => { this.saveError = err?.error?.message ?? 'Creation failed.'; this.cdr.detectChanges(); }
      });
    }
  }

  // ── Delete ────────────────────────────────────────────────────────────────

  askDelete(r: Registry): void {
    this.deletingRegistry = r;
    this.cdr.detectChanges();
  }

  confirmDelete(): void {
    if (!this.deletingRegistry) return;
    this.registryService.delete(this.deletingRegistry.id).subscribe({
      next: () => { this.deletingRegistry = null; this.load(); },
      error: () => { this.deletingRegistry = null; this.cdr.detectChanges(); }
    });
  }

  cancelDelete(): void {
    this.deletingRegistry = null;
    this.cdr.detectChanges();
  }
}