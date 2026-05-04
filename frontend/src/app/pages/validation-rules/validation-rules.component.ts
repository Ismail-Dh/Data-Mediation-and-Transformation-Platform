import { Component, OnInit, signal, inject, computed, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { ValidationRuleService } from '../../services/validation/validation-rule.service';
import { ValidationRule, CreateValidationRuleRequest, UpdateValidationRuleRequest, RuleType } from '../../models/validation-rule';

@Component({
  selector: 'app-validation-rules',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, MatSnackBarModule],
  templateUrl: './validation-rules.component.html',
  styleUrl:    './validation-rules.component.scss'
})
export class ValidationRulesComponent implements OnInit {
  private fb      = inject(FormBuilder);
  private service = inject(ValidationRuleService);
  private snack   = inject(MatSnackBar);
  private cdr     = inject(ChangeDetectorRef);

  rules         = signal<ValidationRule[]>([]);
  loading       = signal(true);
  showForm      = signal(false);
  editingRule   = signal<ValidationRule | null>(null);
  deletingRule  = signal<ValidationRule | null>(null);

  // ── Filtres (signals) ────────────────────────────────────────────────────
  searchQuery  = signal('');
  filterType   = signal('');
  filterActive = signal('');

  filtered = computed(() => {
    const q      = this.searchQuery().trim().toLowerCase();
    const type   = this.filterType();
    const active = this.filterActive();

    return this.rules().filter(r => {
      const matchQ      = !q || r.fieldName.toLowerCase().includes(q) || (r.pattern ?? '').toLowerCase().includes(q);
      const matchType   = !type   || r.ruleType === type;
      const matchActive = active === '' || String(r.active) === active;
      return matchQ && matchType && matchActive;
    });
  });

  ruleTypes: RuleType[] = [
    'NOT_NULL', 'TYPE_NUMBER', 'TYPE_DATE',
    'REGEX_EMAIL', 'REGEX_PHONE', 'MIN_MAX_LENGTH'
  ];

  form = this.fb.group({
    fieldName: ['', [Validators.required, Validators.maxLength(150)]],
    ruleType:  [null as RuleType | null, Validators.required],
    pattern:   [null as string | null],
    description: [null as string | null, Validators.maxLength(500)],
    active:    [true]
  });

  ngOnInit() { this.loadRules(); }

  applyFilter(field: 'search' | 'type' | 'active', value: string) {
    if (field === 'search') this.searchQuery.set(value);
    if (field === 'type')   this.filterType.set(value);
    if (field === 'active') this.filterActive.set(value);
    this.cdr.detectChanges();
  }

  countByStatus(active: boolean): number {
    return this.rules().filter(r => r.active === active).length;
  }

  loadRules() {
    this.loading.set(true);
    this.service.getRules().subscribe({
      next: data => {
        this.rules.set(data);
        this.loading.set(false);
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading.set(false);
        this.notify('Error loading rules', true);
        this.cdr.detectChanges();
      }
    });
  }

  openCreate() {
    this.editingRule.set(null);
    this.form.reset({ active: true });
    this.showForm.set(true);
    this.cdr.detectChanges();
  }

  openEdit(rule: ValidationRule) {
    this.editingRule.set(rule);
    this.form.patchValue({
      fieldName: rule.fieldName,
      ruleType:  rule.ruleType,
      pattern:   rule.pattern,
      description: rule.description,
      active:    rule.active
    });
    this.showForm.set(true);
    this.cdr.detectChanges();
  }

  save() {
    if (this.form.invalid) return;
    const val     = this.form.value;
    const editing = this.editingRule();

    const payload = {
      fieldName: val.fieldName!,
      ruleType:  val.ruleType!,
      pattern:   val.pattern || null,
      description: val.description || null,
      active:    val.active ?? true
    };

    if (editing) {
      this.service.updateRule(editing.id, payload as UpdateValidationRuleRequest).subscribe({
        next: () => {
          this.notify('Rule updated');
          this.showForm.set(false);
          this.loadRules();
          this.cdr.detectChanges();
        },
        error: err => this.handleError(err)
      });
    } else {
      this.service.createRule(payload as CreateValidationRuleRequest).subscribe({
        next: () => {
          this.notify('Rule created');
          this.showForm.set(false);
          this.loadRules();
          this.cdr.detectChanges();
        },
        error: err => this.handleError(err)
      });
    }
  }

  deactivate(rule: ValidationRule) {
    if (!confirm(`Deactivate rule "${rule.fieldName}"?`)) return;
    this.service.deactivateRule(rule.id).subscribe({
      next: () => { this.notify('Rule deactivated'); this.loadRules(); this.cdr.detectChanges(); },
      error: err => this.handleError(err)
    });
  }

  delete(rule: ValidationRule) {
    this.deletingRule.set(rule);
    this.cdr.detectChanges();
  }

  cancelDelete() {
    this.deletingRule.set(null);
    this.cdr.detectChanges();
  }

  confirmDelete() {
    const rule = this.deletingRule();
    if (!rule) return;
    this.service.deleteRule(rule.id).subscribe({
      next: () => {
        this.notify('Rule deleted');
        this.deletingRule.set(null);
        this.loadRules();
        this.cdr.detectChanges();
      },
      error: err => this.handleError(err)
    });
  }

  cancel() {
    this.showForm.set(false);
    this.cdr.detectChanges();
  }

  private handleError(err: any) {
    const status = err.status;
    const body   = err.error;
    if      (status === 400) this.notify(`Validation: ${Object.values(body).join(' | ')}`, true);
    else if (status === 404) this.notify(`Not found: ${body.error}`, true);
    else if (status === 409) this.notify('Cannot delete: rule still used by a pipeline', true);
    else                     this.notify('Internal server error', true);
    this.cdr.detectChanges();
  }

  private notify(msg: string, isError = false) {
    this.snack.open(msg, 'OK', {
      duration: 4000,
      panelClass: isError ? ['snack-error'] : ['snack-success']
    });
  }
}