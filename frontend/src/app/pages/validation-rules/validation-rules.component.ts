import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';

import { ValidationRuleService } from '../../services/validation/validation-rule.service';
import { ValidationRule, CreateValidationRuleRequest, UpdateValidationRuleRequest, RuleType } from '../../models/validation-rule';

@Component({
  selector: 'app-validation-rules',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatSnackBarModule, MatProgressSpinnerModule,
    MatTooltipModule, MatSlideToggleModule
  ],
  templateUrl: './validation-rules.component.html',
  styleUrl:    './validation-rules.component.scss'
})
export class ValidationRulesComponent implements OnInit {
  private fb      = inject(FormBuilder);
  private service = inject(ValidationRuleService);
  private snack   = inject(MatSnackBar);

  rules            = signal<ValidationRule[]>([]);
  loading          = signal(true);
  showForm         = signal(false);
  editingRule      = signal<ValidationRule | null>(null);
  displayedColumns = ['id', 'fieldName', 'ruleType', 'pattern', 'active', 'global', 'actions'];

  ruleTypes: RuleType[] = [
    'NOT_NULL', 'TYPE_NUMBER', 'TYPE_DATE',
    'REGEX_EMAIL', 'REGEX_PHONE', 'MIN_MAX_LENGTH'
  ];

  form = this.fb.group({
    fieldName: ['', [Validators.required, Validators.maxLength(150)]],
    ruleType:  [null as RuleType | null, Validators.required],
    pattern:   [null as string | null],
    active:    [true]
  });

  ngOnInit() { this.loadRules(); }

  loadRules() {
    this.loading.set(true);
    this.service.getRules().subscribe({
      next:  (data) => { this.rules.set(data); this.loading.set(false); },
      error: ()     => { this.loading.set(false); this.notify('Error loading rules', true); }
    });
  }

  openCreate() {
    this.editingRule.set(null);
    this.form.reset({ active: true });
    this.showForm.set(true);
  }

  openEdit(rule: ValidationRule) {
    this.editingRule.set(rule);
    this.form.patchValue({
      fieldName: rule.fieldName,
      ruleType:  rule.ruleType,
      pattern:   rule.pattern,
      active:    rule.active
    });
    this.showForm.set(true);
  }

  save() {
    if (this.form.invalid) return;
    const val     = this.form.value;
    const editing = this.editingRule();

    const payload = {
      fieldName: val.fieldName!,
      ruleType:  val.ruleType!,
      pattern:   val.pattern || null,
      active:    val.active ?? true
    };

    if (editing) {
      this.service.updateRule(editing.id, payload as UpdateValidationRuleRequest).subscribe({
        next:  () => { this.notify('Rule updated ✅'); this.showForm.set(false); this.loadRules(); },
        error: (err) => this.handleError(err)
      });
    } else {
      this.service.createRule(payload as CreateValidationRuleRequest).subscribe({
        next:  () => { this.notify('Rule created ✅'); this.showForm.set(false); this.loadRules(); },
        error: (err) => this.handleError(err)
      });
    }
  }

  deactivate(rule: ValidationRule) {
    if (!confirm(`Deactivate rule "${rule.fieldName}"?`)) return;
    this.service.deactivateRule(rule.id).subscribe({
      next:  () => { this.notify('Rule deactivated'); this.loadRules(); },
      error: (err) => this.handleError(err)
    });
  }

  delete(rule: ValidationRule) {
    if (!confirm(`Permanently delete rule "${rule.fieldName}"?`)) return;
    this.service.deleteRule(rule.id).subscribe({
      next:  () => { this.notify('Rule deleted'); this.loadRules(); },
      error: (err) => this.handleError(err)
    });
  }

  cancel() { this.showForm.set(false); }

  private handleError(err: any) {
    const status = err.status;
    const body   = err.error;
    if (status === 400)      this.notify(`Validation: ${Object.values(body).join(' | ')}`, true);
    else if (status === 404) this.notify(`Not found: ${body.error}`, true);
    else if (status === 409) this.notify('Cannot delete: rule is still used by a pipeline', true);
    else                     this.notify('Internal server error', true);
  }

  private notify(msg: string, isError = false) {
    this.snack.open(msg, 'OK', {
      duration: 4000,
      panelClass: isError ? ['snack-error'] : ['snack-success']
    });
  }
}