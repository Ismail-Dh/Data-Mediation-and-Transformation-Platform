import { Component, Input, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { ProcessApiService } from '../../../../../app/services/process/process-api-service';
import {
  ResponseMappingRule,
  CreateResponseMappingRuleRequest,
  MappingType,
  RESPONSE_MAPPING_TYPES,
  EXPRESSION_HINTS,
  NEEDS_EXPRESSION,
  SOURCE_HIDDEN,
} from '../../../../../app/models/process.model';
import { Provider } from '../../../../../app/models/provider';
import { ProviderService } from '../../../../../app/services/provider/provider-service';

@Component({
  selector: 'app-pipeline-response-rules-tab',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './pipeline-response-rules-tab-component.html',
  styleUrl: './pipeline-response-rules-tab-component.scss',
})
export class PipelineResponseRulesTabComponent implements OnInit {

  @Input() pipelineId!: number;

  rules:     ResponseMappingRule[] = [];
  providers: Provider[]            = [];

  loading  = false;
  success: string | null = null;
  error:   string | null = null;

  showForm   = false;
  editingId: number | null = null;
  form!:     FormGroup;

  // Constantes exposées au template
  readonly mappingTypes = RESPONSE_MAPPING_TYPES;

  constructor(
    private api:             ProcessApiService,
    private providerService: ProviderService,
    private fb:              FormBuilder,
    private cdr:             ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.buildForm();
    this.loadRules();
    this.providerService.getAll().subscribe(p => {
      this.providers = p;
      this.cdr.detectChanges();
    });
  }

  // ── Form ───────────────────────────────────────────────────────────────────

  private buildForm(): void {
    this.form = this.fb.group({
      sourceField: ['', Validators.required],
      targetField: ['', Validators.required],
      mappingType: ['FIELD_PLACEMENT' as MappingType, Validators.required],
      expression:  [''],
      required:    [false],
      providerId:  [null],
    });

    // Quand le type change, on adapte les validators
    this.form.get('mappingType')!.valueChanges.subscribe((type: MappingType) => {
      this.updateValidators(type);
      this.cdr.detectChanges();
    });
  }

  private updateValidators(type: MappingType): void {
    const srcCtrl  = this.form.get('sourceField')!;
    const exprCtrl = this.form.get('expression')!;

    // CALCULATED_FIELD : le champ source est ignoré (l'expression fournit les champs)
    if (SOURCE_HIDDEN[type]) {
      srcCtrl.clearValidators();
      srcCtrl.setValue('N/A');
    } else {
      srcCtrl.setValidators(Validators.required);
      if (srcCtrl.value === 'N/A') srcCtrl.setValue('');
    }

    // Expression obligatoire pour FORMAT_CHANGE, VALUE_TRANSFORM, CALCULATED_FIELD
    if (NEEDS_EXPRESSION[type]) {
      exprCtrl.setValidators(Validators.required);
    } else {
      exprCtrl.clearValidators();
      exprCtrl.setValue('');
    }

    srcCtrl.updateValueAndValidity();
    exprCtrl.updateValueAndValidity();
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  get currentType(): MappingType {
    return this.form.get('mappingType')?.value as MappingType;
  }

  get needsExpression(): boolean {
    return NEEDS_EXPRESSION[this.currentType] ?? false;
  }

  get sourceHidden(): boolean {
    return SOURCE_HIDDEN[this.currentType] ?? false;
  }

  get expressionHint(): string {
    return EXPRESSION_HINTS[this.currentType] ?? '';
  }

  // ── CRUD ───────────────────────────────────────────────────────────────────

  loadRules(): void {
    this.loading = true;
    this.error   = null;
    this.api.getRules(this.pipelineId).subscribe({
      next: rules => {
        this.rules   = rules;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.error   = err?.error?.message ?? 'Failed to load rules';
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  openCreate(): void {
    this.editingId = null;
    this.form.reset({
      mappingType: 'FIELD_PLACEMENT',
      required:    false,
      providerId:  null,
    });
    this.showForm = true;
    this.cdr.detectChanges();
  }

  openEdit(rule: ResponseMappingRule): void {
    this.editingId = rule.id;
    this.form.patchValue({
      sourceField: rule.sourceField,
      targetField: rule.targetField,
      mappingType: rule.mappingType,
      expression:  rule.expression ?? '',
      required:    rule.required,
      providerId:  rule.providerId,
    });
    this.showForm = true;
    this.updateValidators(rule.mappingType);
    this.cdr.detectChanges();
  }

  save(): void {
    if (this.form.invalid) return;
    const val = this.form.value as CreateResponseMappingRuleRequest;

    const obs = this.editingId !== null
      ? this.api.updateRule(this.pipelineId, this.editingId, val)
      : this.api.createRule(this.pipelineId, val);

    obs.subscribe({
      next: () => {
        this.success  = this.editingId ? 'Rule updated.' : 'Rule created.';
        this.showForm = false;
        this.editingId = null;
        this.loadRules();
        setTimeout(() => { this.success = null; this.cdr.detectChanges(); }, 3000);
      },
      error: err => {
        this.error = err?.error?.message ?? 'Save failed';
        this.cdr.detectChanges();
      },
    });
  }

  delete(ruleId: number): void {
    if (!confirm('Delete this response mapping rule?')) return;
    this.api.deleteRule(this.pipelineId, ruleId).subscribe({
      next: () => {
        this.success = 'Rule deleted.';
        this.loadRules();
        setTimeout(() => { this.success = null; this.cdr.detectChanges(); }, 3000);
      },
      error: err => {
        this.error = err?.error?.message ?? 'Delete failed';
        this.cdr.detectChanges();
      },
    });
  }

  cancelForm(): void {
    this.showForm  = false;
    this.editingId = null;
    this.form.reset({ mappingType: 'FIELD_PLACEMENT', required: false });
    this.cdr.detectChanges();
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  providerLabel(providerId: number | null): string {
    if (!providerId) return 'All providers';
    return this.providers.find(p => p.id === providerId)?.name ?? `Provider #${providerId}`;
  }

  typeLabel(type: MappingType): string {
    const labels: Record<MappingType, string> = {
      FIELD_PLACEMENT:  'Field Placement',
      FORMAT_CHANGE:    'Format Change',
      VALUE_TRANSFORM:  'Value Transform',
      CALCULATED_FIELD: 'Calculated Field',
      RESTRUCTURING:    'Restructuring',
    };
    return labels[type] ?? type;
  }

  trackById(_: number, r: ResponseMappingRule): number { return r.id; }
}