import { Component, Input, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { MappingRuleService } from '../../../../services/mapping/mapping-rule-service';
import { MappingRuleResponse } from '../../../../models/mapping-rule';
import { MappingResultResponse } from '../../../../models/mapping-result';

@Component({
  selector: 'app-pipeline-mapping-tab',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './pipeline-mapping-tab-component.html',
  styleUrl: './pipeline-mapping-tab-component.scss'
})
export class PipelineMappingTabComponent implements OnInit {

  @Input() pipelineId!: number;

  mappingRules: MappingRuleResponse[] = [];
  loading = false;

  mappingForm!: FormGroup;
  editingRuleId: number | null = null;

  // Test zone
  testPayload = '';
  testResult:  MappingResultResponse | null = null;
  testError:   string | null = null;
  testLoading  = false;

  readonly expressionHints: Record<string, string> = {
    FIELD_PLACEMENT:  '',
    VALUE_TRANSFORM:  'e.g. UPPERCASE · LOWERCASE · TRIM · CONCAT: :firstName:lastName · SPLIT:@:0 · REGEX_REPLACE:[^0-9]:',
    FORMAT_CHANGE:    'e.g. STRING_TO_INT · STRING_TO_DOUBLE · DATE_TO_UNIX · UNIX_TO_DATE · dd/MM/yyyy|yyyy-MM-dd',
    CALCULATED_FIELD: 'e.g. {price} * (1 + {tax}) · IF:amount:gt:1000:VIP:STD · SUM:items[].price · AVG:items[].qty',
    RESTRUCTURING:    'Leave empty to NEST (move field to nested path) · or type FLATTEN to flatten an object'
  };

  constructor(
    private mappingService: MappingRuleService,
    private fb:             FormBuilder,
    private cdr:            ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.mappingForm = this.fb.group({
      sourceField: ['', Validators.required],
      targetField: ['', Validators.required],
      mappingType: ['', Validators.required],
      expression:  ['']
    });

    // React to mappingType changes
    this.mappingForm.get('mappingType')!.valueChanges.subscribe(type => {
      this.mappingForm.get('expression')!.setValue('');
      this.updateSourceFieldValidator(type);
      this.updateTargetFieldValidator();
      this.cdr.detectChanges();
    });

    // React to expression changes — targetField validator depends on FLATTEN
    this.mappingForm.get('expression')!.valueChanges.subscribe(() => {
      this.updateTargetFieldValidator();
      this.cdr.detectChanges();
    });

    this.loadRules();
  }

  // ── Validators ────────────────────────────────────────────────────────────

  private updateSourceFieldValidator(type: string): void {
    const ctrl = this.mappingForm.get('sourceField')!;
    if (type === 'CALCULATED_FIELD') {
      ctrl.clearValidators();
      ctrl.setValue('N/A');
    } else {
      ctrl.setValidators(Validators.required);
      ctrl.setValue('');
    }
    ctrl.updateValueAndValidity();
  }

  private updateTargetFieldValidator(): void {
    const ctrl = this.mappingForm.get('targetField')!;
    if (this.targetFieldHidden) {
      ctrl.clearValidators();
      ctrl.setValue('N/A');
    } else {
      ctrl.setValidators(Validators.required);
    }
    ctrl.updateValueAndValidity();
  }

  // ── Getters ───────────────────────────────────────────────────────────────

  get needsExpression(): boolean {
    const t = this.mappingForm.get('mappingType')?.value;
    return ['VALUE_TRANSFORM', 'FORMAT_CHANGE', 'CALCULATED_FIELD', 'RESTRUCTURING'].includes(t);
  }

  get currentHint(): string {
    return this.expressionHints[this.mappingForm.get('mappingType')?.value] ?? '';
  }

  // CALCULATED_FIELD — sourceField ignored by backend
  get sourceFieldHidden(): boolean {
    return this.mappingForm.get('mappingType')?.value === 'CALCULATED_FIELD';
  }

  // RESTRUCTURING + FLATTEN — targetField ignored by backend
  get targetFieldHidden(): boolean {
    const type = this.mappingForm.get('mappingType')?.value;
    const expr = this.mappingForm.get('expression')?.value?.trim().toUpperCase();
    return type === 'RESTRUCTURING' && expr === 'FLATTEN';
  }

  // ── Load ──────────────────────────────────────────────────────────────────

  private loadRules(): void {
    this.loading = true;
    this.mappingService.getAllRules(this.pipelineId).subscribe({
      next: data => {
        this.mappingRules = data;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  // ── Save (create or update) ───────────────────────────────────────────────

  saveMappingRule(): void {
    if (this.mappingForm.invalid) return;
    const val = this.mappingForm.value;

    if (this.editingRuleId !== null) {
      // No update endpoint — delete + recreate pattern
      this.mappingService.deleteRule(this.pipelineId, this.editingRuleId).subscribe(() => {
        this.mappingService.createRule(this.pipelineId, val).subscribe(() => {
          this.cancelEdit();
          this.loadRules();
        });
      });
    } else {
      this.mappingService.createRule(this.pipelineId, val).subscribe(() => {
        this.mappingForm.reset();
        this.loadRules();
      });
    }
  }

  // ── Edit ──────────────────────────────────────────────────────────────────

  openEdit(r: MappingRuleResponse): void {
    this.editingRuleId = r.id;
    this.mappingForm.patchValue({
      sourceField: r.sourceField,
      targetField: r.targetField,
      mappingType: r.mappingType,
      expression:  r.expression ?? ''
    });
    this.cdr.detectChanges();
  }

  cancelEdit(): void {
    this.editingRuleId = null;
    this.mappingForm.reset();
    this.cdr.detectChanges();
  }

  // ── Toggle active / inactive ──────────────────────────────────────────────

  toggleRule(r: MappingRuleResponse): void {
    if (r.active) {
      this.mappingService.deleteRule(this.pipelineId, r.id).subscribe(() => this.loadRules());
    } else {
      this.mappingService.activateRule(this.pipelineId, r.id).subscribe(() => this.loadRules());
    }
  }

  // ── Delete ────────────────────────────────────────────────────────────────

  deleteRule(id: number): void {
    this.mappingService.deleteRule(this.pipelineId, id).subscribe(() => this.loadRules());
  }

  // ── Apply test ────────────────────────────────────────────────────────────

  applyTest(): void {
    if (!this.testPayload.trim()) return;
    this.testLoading = true;
    this.testResult  = null;
    this.testError   = null;

    this.mappingService.applyMapping(this.pipelineId, this.testPayload).subscribe({
      next: result => {
        this.testResult  = result;
        this.testLoading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.testError   = err?.error?.message ?? 'Invalid JSON or server error.';
        this.testLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  // ── Labels ────────────────────────────────────────────────────────────────

  mappingTypeLabel(type: string): string {
    const labels: Record<string, string> = {
      'FIELD_PLACEMENT':  'Field Placement',
      'FORMAT_CHANGE':    'Format Change',
      'VALUE_TRANSFORM':  'Value Transform',
      'CALCULATED_FIELD': 'Calculated Field',
      'RESTRUCTURING':    'Restructuring'
    };
    return labels[type] ?? type;
  }
}