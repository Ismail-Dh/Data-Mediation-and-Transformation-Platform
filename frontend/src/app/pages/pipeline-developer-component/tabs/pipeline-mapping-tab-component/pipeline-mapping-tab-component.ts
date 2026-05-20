import { Component, Input, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { MappingRuleService } from '../../../../services/mapping/mapping-rule-service';
import { PipelineFieldService } from '../../../../services/pipelineField/pipeline-field-service';
import { MappingRuleResponse } from '../../../../models/mapping-rule';
import { MappingResultResponse } from '../../../../models/mapping-result';
import { PipelineFieldResponse } from '../../../../models/pipelineField';

@Component({
  selector: 'app-pipeline-mapping-tab',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './pipeline-mapping-tab-component.html',
  styleUrl: './pipeline-mapping-tab-component.scss'
})
export class PipelineMappingTabComponent implements OnInit {

  @Input() pipelineId!: number;

  mappingRules:   MappingRuleResponse[]  = [];
  pipelineFields: PipelineFieldResponse[] = [];
  loading = false;

  mappingForm!: FormGroup;
  editingRuleId: number | null = null;

  // Test zone
  testPayload  = '';
  testResult:  MappingResultResponse | null = null;
  testError:   string | null = null;
  testLoading  = false;

  constructor(
    private mappingService: MappingRuleService,
    private fieldsService:  PipelineFieldService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.mappingForm = this.fb.group({
      sourceField:  ['', Validators.required],
      targetField:  ['', Validators.required],
      mappingType:  ['', Validators.required]
    });
    this.loadRules();
  }

  // ── Load ──────────────────────────────────────────────────────────────────
  private loadRules(): void {
    this.loading = true;
    this.fieldsService.getFields(this.pipelineId).subscribe(data => {
      this.pipelineFields = data;
      this.cdr.detectChanges();
    });
    // Fetch all rules (active + inactive) so the user can re-activate them
    this.mappingService.getAllRules(this.pipelineId).subscribe({
      next: data => { this.mappingRules = data; this.loading = false; this.cdr.detectChanges(); },
      error: ()  => { this.loading = false; this.cdr.detectChanges(); }
    });
  }

  // ── Save (create or update) ────────────────────────────────────────────────
  saveMappingRule(): void {
    if (this.mappingForm.invalid) return;
    const val = this.mappingForm.value;

    if (this.editingRuleId !== null) {
      // No update endpoint in the API — delete + recreate pattern
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
      mappingType: r.mappingType
    });
    this.cdr.detectChanges();
  }

  cancelEdit(): void {
    this.editingRuleId = null;
    this.mappingForm.reset();
    this.cdr.detectChanges();
  }

  // ── Toggle active / inactive ───────────────────────────────────────────────
  toggleRule(r: MappingRuleResponse): void {
    if (r.active) {
      // Soft-delete → inactive
      this.mappingService.deleteRule(this.pipelineId, r.id).subscribe(() => this.loadRules());
    } else {
      // Re-activate
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