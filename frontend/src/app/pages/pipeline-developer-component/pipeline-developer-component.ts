import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { PipelineService } from '../../services/pipeline/pipeline-service';
import { ProviderService } from '../../services/provider/provider-service';
import { PipelineFieldService } from '../../services/pipelineField/pipeline-field-service';
import { PayloadService } from '../../services/payload/payload-service';
import { Pipeline } from '../../models/pipeline';
import { Provider } from '../../models/provider';
import { PipelineFieldResponse } from '../../models/pipelineField';
import {
  PayloadResponse,
  FieldViolation,
  PayloadValidationError,
  ValidationPreviewResponse
} from '../../models/payload';

type DetailTab = 'info' | 'fields' | 'payloads';

@Component({
  selector: 'app-pipeline-developer',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './pipeline-developer-component.html',
  styleUrls: ['./pipeline-developer-component.scss']
})
export class PipelineDeveloperComponent implements OnInit {

  pipelines: Pipeline[] = [];
  filtered:  Pipeline[] = [];
  providers: Provider[] = [];

  searchQuery  = '';
  filterStatus = '';

  showModal         = false;
  editingId: number | null = null;
  deletingPipeline: Pipeline | null = null;
  detailPipeline:   Pipeline | null = null;

  form!: FormGroup;

  readonly FORMATS     = ['JSON', 'XML', 'CSV', 'PLAIN_TEXT'];
  readonly STATUSES    = ['DRAFT', 'CONFIGURED', 'VALIDATED'];
  readonly FIELD_TYPES = ['STRING', 'INTEGER', 'BOOLEAN', 'OBJECT', 'ARRAY'];

  detailTab: DetailTab = 'info';

  // Pipeline Fields
  pipelineFields: PipelineFieldResponse[] = [];
  fieldsLoading   = false;
  fieldForm!: FormGroup;
  editingFieldId: number | null = null;

  // Payloads
  payloads: PayloadResponse[] = [];
  payloadsLoading   = false;
  payloadForm!: FormGroup;
  payloadSubmitting = false;
  payloadGenericError: string | null = null;   // non-422 errors
  payloadViolations:   FieldViolation[] = [];  // 422 structural violations
  payloadSuccess: PayloadResponse | null = null;

  // Preview
  previewForm!: FormGroup;
  previewResult: ValidationPreviewResponse | null = null;
  previewLoading = false;

  constructor(
    private pipelineService: PipelineService,
    private providerService: ProviderService,
    private pipelineFieldService: PipelineFieldService,
    private payloadService: PayloadService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.buildForm();
    this.buildFieldForm();
    this.buildPayloadForm();
    this.buildPreviewForm();
    this.loadPipelines();
    this.providerService.getAll().subscribe(data => {
      this.providers = data;
      this.cdr.detectChanges();
    });
  }

  private buildForm(): void {
    this.form = this.fb.group({
      name:         ['', Validators.required],
      version:      [''],
      inputFormat:  ['', Validators.required],
      outputFormat: ['', Validators.required],
      providerId:   [null]
    });
  }

  private buildFieldForm(): void {
    this.fieldForm = this.fb.group({
      fieldPath: ['', Validators.required],
      fieldType: ['', Validators.required],
      required:  [false],
      nullable:  [false]
    });
  }

  private buildPayloadForm(): void {
    this.payloadForm = this.fb.group({
      rawContent: ['', Validators.required],
      format:     ['', Validators.required]
    });
  }

  private buildPreviewForm(): void {
    this.previewForm = this.fb.group({
      rawContent: ['', Validators.required],
      format:     ['JSON']
    });
  }

  private loadPipelines(): void {
    this.pipelineService.getMine().subscribe(data => {
      this.pipelines = data;
      this.applyFilters();
      this.cdr.detectChanges();
    });
  }

  applyFilters(): void {
    let result = [...this.pipelines];
    const q = this.searchQuery.trim().toLowerCase();
    if (q) result = result.filter(p => p.name.toLowerCase().includes(q) || (p.providerName ?? '').toLowerCase().includes(q));
    if (this.filterStatus) result = result.filter(p => p.status === this.filterStatus);
    this.filtered = result;
    this.cdr.detectChanges();
  }

  openCreate(): void { this.editingId = null; this.form.reset(); this.showModal = true; this.cdr.detectChanges(); }

  openEdit(p: Pipeline): void {
    this.editingId = p.id;
    this.form.patchValue({ name: p.name, version: p.version, inputFormat: p.inputFormat, outputFormat: p.outputFormat, providerId: p.providerId ?? null });
    this.showModal = true;
    this.cdr.detectChanges();
  }

  closeModal(): void { this.showModal = false; this.editingId = null; this.cdr.detectChanges(); }

  save(): void {
    if (this.form.invalid) return;
    const val = this.form.value;
    if (this.editingId !== null) {
      this.pipelineService.update(this.editingId, val).subscribe(() => { this.loadPipelines(); this.closeModal(); });
    } else {
      this.pipelineService.create(val).subscribe(() => { this.loadPipelines(); this.closeModal(); });
    }
  }

  askDelete(p: Pipeline): void { this.deletingPipeline = p; this.cdr.detectChanges(); }
  cancelDelete(): void { this.deletingPipeline = null; this.cdr.detectChanges(); }
  confirmDelete(): void {
    if (!this.deletingPipeline) return;
    this.pipelineService.delete(this.deletingPipeline.id).subscribe(() => {
      this.deletingPipeline = null; this.loadPipelines();
    });
  }

  openDetail(p: Pipeline): void {
    this.detailPipeline = p;
    this.detailTab      = 'info';
    this.pipelineFields = [];
    this.payloads       = [];
    this.resetPayloadState();
    this.cancelEditField();
    this.cdr.detectChanges();
  }

  closeDetail(): void {
    this.detailPipeline = null;
    this.pipelineFields = [];
    this.payloads       = [];
    this.resetPayloadState();
    this.cancelEditField();
    this.cdr.detectChanges();
  }

  setDetailTab(tab: DetailTab): void {
    this.detailTab = tab;
    if (!this.detailPipeline) return;
    if (tab === 'fields'   && this.pipelineFields.length === 0) this.loadFields(this.detailPipeline.id);
    if (tab === 'payloads' && this.payloads.length === 0)       this.loadPayloads(this.detailPipeline.id);
    this.cdr.detectChanges();
  }

  // Pipeline Fields
  private loadFields(pipelineId: number): void {
    this.fieldsLoading = true;
    this.pipelineFieldService.getFields(pipelineId).subscribe({
      next: data => { this.pipelineFields = data; this.fieldsLoading = false; this.cdr.detectChanges(); },
      error: ()  => { this.fieldsLoading = false; this.cdr.detectChanges(); }
    });
  }

  saveField(): void {
    if (this.fieldForm.invalid || !this.detailPipeline) return;
    const val = this.fieldForm.value;
    if (this.editingFieldId !== null) {
      this.pipelineFieldService.updateField(this.detailPipeline.id, this.editingFieldId, val).subscribe(() => {
        this.loadFields(this.detailPipeline!.id); this.cancelEditField();
      });
    } else {
      this.pipelineFieldService.addField(this.detailPipeline.id, val).subscribe(() => {
        this.loadFields(this.detailPipeline!.id);
        this.fieldForm.reset({ required: false, nullable: false });
      });
    }
  }

  openEditField(f: PipelineFieldResponse): void {
    this.editingFieldId = f.id;
    this.fieldForm.patchValue({ fieldPath: f.fieldPath, fieldType: f.fieldType, required: f.required, nullable: f.nullable });
    this.cdr.detectChanges();
  }

  cancelEditField(): void { this.editingFieldId = null; this.fieldForm.reset({ required: false, nullable: false }); this.cdr.detectChanges(); }

  deleteField(fieldId: number): void {
    if (!this.detailPipeline) return;
    this.pipelineFieldService.deleteField(this.detailPipeline.id, fieldId).subscribe(() => this.loadFields(this.detailPipeline!.id));
  }

  // Payloads
  private loadPayloads(pipelineId: number): void {
    this.payloadsLoading = true;
    this.payloadService.getPayloadsByPipeline(pipelineId).subscribe({
      next: data => { this.payloads = data; this.payloadsLoading = false; this.cdr.detectChanges(); },
      error: ()  => { this.payloadsLoading = false; this.cdr.detectChanges(); }
    });
  }

  submitPayload(): void {
    if (this.payloadForm.invalid || !this.detailPipeline) return;
    this.payloadSubmitting   = true;
    this.payloadGenericError = null;
    this.payloadViolations   = [];
    this.payloadSuccess      = null;

    this.payloadService.receivePayload(this.detailPipeline.id, this.payloadForm.value).subscribe({
      next: res => {
        this.payloadSubmitting = false;
        this.payloadSuccess    = res;
        this.payloadForm.reset();
        this.loadPayloads(this.detailPipeline!.id);
        this.cdr.detectChanges();
      },
      error: err => {
        this.payloadSubmitting = false;
        if (err.status === 422 && err.error?.violations) {
          // 422 with structured violations list
          const body = err.error as PayloadValidationError;
          this.payloadViolations = body.violations;
        } else {
          this.payloadGenericError = err?.error?.error ?? 'Submission failed.';
        }
        this.loadPayloads(this.detailPipeline!.id); // show FAILED entry
        this.cdr.detectChanges();
      }
    });
  }

  // Preview
  runPreview(): void {
    if (this.previewForm.invalid || !this.detailPipeline) return;
    this.previewLoading = true;
    this.previewResult  = null;
    this.payloadService.previewValidation(this.detailPipeline.id, this.previewForm.value).subscribe({
      next: res => { this.previewResult = res; this.previewLoading = false; this.cdr.detectChanges(); },
      error: ()  => { this.previewLoading = false; this.cdr.detectChanges(); }
    });
  }

  clearPreview(): void { this.previewResult = null; this.previewForm.reset({ format: 'JSON' }); this.cdr.detectChanges(); }

  private resetPayloadState(): void {
    this.payloadSubmitting   = false;
    this.payloadGenericError = null;
    this.payloadViolations   = [];
    this.payloadSuccess      = null;
    this.previewResult       = null;
    this.payloadForm?.reset();
    this.previewForm?.reset({ format: 'JSON' });
  }

  violationLabel(errorType: string): string {
    switch (errorType) {
      case 'MISSING_FIELD':    return 'Champ manquant';
      case 'TYPE_MISMATCH':    return 'Type incorrect';
      case 'NULL_NOT_ALLOWED': return 'Null interdit';
      case 'INVALID_JSON':     return 'JSON invalide';
      default:                 return errorType;
    }
  }

  trackById(_: number, p: Pipeline): number { return p.id; }
}