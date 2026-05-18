import { Component, Input, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { PipelineValidationRuleService } from '../../../../services/pipeline-validation/pipeline-validation-rule-service.service';
import { PipelineFieldService } from '../../../../services/pipelineField/pipeline-field-service';
import { ValidationRule, PipelineValidationRuleResponse } from '../../../../models/validation-rule';
import { PipelineFieldResponse } from '../../../../models/pipelineField';

@Component({
  selector: 'app-pipeline-rules-tab',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './pipeline-rules-tab.component.html',
  styleUrl: './pipeline-rules-tab.component.scss' 

})
export class PipelineRulesTabComponent implements OnInit, OnDestroy {

  @Input() pipelineId!: number;

  validationRules: PipelineValidationRuleResponse[] = [];
  globalRules: ValidationRule[] = [];
  pipelineFields: PipelineFieldResponse[] = [];
  loading = false;

  ruleForm!: FormGroup;
  editingRuleId: number | null = null;
  selectedGlobalRuleId: number | null = null;
  selectedFieldPath: string | null = null;
  currentSuggestions: { label: string; value: string }[] = [];

  private ruleTypeSub?: Subscription;

  readonly PATTERN_SUGGESTIONS: Record<string, { label: string; value: string }[]> = {
    REGEX_EMAIL:    [
      { label: 'Standard email', value: '^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$' }
    ],
    REGEX_PHONE:    [
      { label: 'FR mobile', value: '^0[67][0-9]{8}$' },
      { label: '+33', value: '^\\+33[67][0-9]{8}$' }
    ],
    REGEX_PASSWORD: [
      { label: 'Min 8, 1 maj, 1 chiffre', value: '^(?=.*[A-Z])(?=.*\\d).{8,}$' }
    ],
    REGEX: [
      { label: 'Lettres', value: '^[a-zA-Z]+$' },
      { label: 'Chiffres', value: '^[0-9]+$' },
      { label: 'UUID', value: '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' }
    ],
    MIN_MAX_LENGTH: [
      { label: '3-30', value: '3,30' },
      { label: '5-100', value: '5,100' },
      { label: '1-255', value: '1,255' }
    ],
    TYPE_NUMBER: [
      { label: 'Entier positif', value: '^[0-9]+$' },
      { label: 'Décimal', value: '^[0-9]+(\\.[0-9]+)?$' }
    ],
    TYPE_DATE: [
      { label: 'yyyy-MM-dd', value: 'yyyy-MM-dd' },
      { label: 'dd/MM/yyyy', value: 'dd/MM/yyyy' }
    ]
  };

  constructor(
    private rulesService: PipelineValidationRuleService,
    private fieldsService: PipelineFieldService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.ruleForm = this.fb.group({
      fieldName: [''],
      ruleType:  [''],
      pattern:   [null]
    });
    this.ruleTypeSub = this.ruleForm.get('ruleType')!.valueChanges.subscribe(type => {
      this.currentSuggestions = this.PATTERN_SUGGESTIONS[type] ?? [];
      this.cdr.detectChanges();
    });
    this.loadAll();
  }

  ngOnDestroy(): void { this.ruleTypeSub?.unsubscribe(); }

  private loadAll(): void {
    this.loading = true;
    this.rulesService.getGlobalRules().subscribe(data => { this.globalRules = data; this.cdr.detectChanges(); });
    this.fieldsService.getFields(this.pipelineId).subscribe(data => { this.pipelineFields = data; this.cdr.detectChanges(); });
    this.rulesService.getRules(this.pipelineId).subscribe({
      next: data => { this.validationRules = data; this.loading = false; this.cdr.detectChanges(); },
      error: ()  => { this.loading = false; this.cdr.detectChanges(); }
    });
  }

  attachGlobalRule(): void {
    if (!this.selectedGlobalRuleId || !this.selectedFieldPath) return;
    const globalRule = this.globalRules.find(r => r.id === this.selectedGlobalRuleId);
    if (!globalRule) return;
    this.rulesService.addRule(this.pipelineId, {
      globalRuleId: this.selectedGlobalRuleId,
      fieldName:    this.selectedFieldPath,
      ruleType:     globalRule.ruleType,
      pattern:      globalRule.pattern,
      active:       true
    }).subscribe(() => {
      this.selectedGlobalRuleId = null;
      this.selectedFieldPath    = null;
      this.loadAll();
    });
  }

  savePrivateRule(): void {
    if (this.ruleForm.invalid) return;
    const val = this.ruleForm.value;
    if (this.editingRuleId !== null) {
      this.rulesService.updateRule(this.pipelineId, this.editingRuleId, val).subscribe(() => {
        this.cancelEdit(); this.loadAll();
      });
    } else {
      this.rulesService.addRule(this.pipelineId, { ...val, active: true }).subscribe(() => {
        this.ruleForm.reset(); this.currentSuggestions = []; this.loadAll();
      });
    }
  }

  openEdit(r: PipelineValidationRuleResponse): void {
    if (r.global) return;
    this.editingRuleId = r.id;
    this.ruleForm.patchValue({ fieldName: r.fieldName, ruleType: r.ruleType, pattern: r.pattern });
    this.currentSuggestions = this.PATTERN_SUGGESTIONS[r.ruleType] ?? [];
    this.cdr.detectChanges();
  }

  cancelEdit(): void {
    this.editingRuleId = null;
    this.ruleForm.reset();
    this.currentSuggestions = [];
    this.cdr.detectChanges();
  }

  toggleRule(id: number): void {
    this.rulesService.toggleRule(this.pipelineId, id).subscribe(() => this.loadAll());
  }

  deleteRule(id: number): void {
    this.rulesService.deleteRule(this.pipelineId, id).subscribe(() => this.loadAll());
  }

  applyPattern(value: string): void {
    this.ruleForm.patchValue({ pattern: value });
    this.cdr.detectChanges();
  }

  ruleTypeLabel(ruleType: string): string {
    const labels: Record<string, string> = {
      'NOT_NULL': 'Not Null', 'TYPE_NUMBER': 'Number', 'TYPE_DATE': 'Date',
      'REGEX_EMAIL': 'Email', 'REGEX_PHONE': 'Phone', 'REGEX': 'Regex',
      'MIN_MAX_LENGTH': 'Min/Max Length'
    };
    return labels[ruleType] ?? ruleType;
  }
}