import { Component, Input, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { PayloadService } from '../../../../services/payload/payload-service';
import {
  PayloadResponse, FieldViolation,
  PayloadValidationError, ValidationPreviewResponse
} from '../../../../models/payload';

@Component({
  selector: 'app-pipeline-payloads-tab',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './pipeline-payloads-tab.component.html',
  styleUrl: './pipeline-payloads-tab.component.scss' 

})
export class PipelinePayloadsTabComponent implements OnInit {

  @Input() pipelineId!: number;

  readonly FORMATS = ['JSON', 'XML', 'CSV', 'PLAIN_TEXT'];

  payloads: PayloadResponse[] = [];
  payloadsLoading = false;
  payloadForm!: FormGroup;
  payloadSubmitting = false;
  payloadGenericError: string | null = null;
  payloadViolations: FieldViolation[] = [];
  payloadSuccess: PayloadResponse | null = null;

  previewForm!: FormGroup;
  previewResult: ValidationPreviewResponse | null = null;
  previewLoading = false;

  constructor(
    private payloadService: PayloadService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.payloadForm = this.fb.group({
      rawContent: ['', Validators.required],
      format:     ['', Validators.required]
    });
    this.previewForm = this.fb.group({
      rawContent: ['', Validators.required],
      format:     ['JSON']
    });
    this.loadPayloads();
  }

  loadPayloads(): void {
    this.payloadsLoading = true;
    this.payloadService.getPayloadsByPipeline(this.pipelineId).subscribe({
      next: data => { this.payloads = data; this.payloadsLoading = false; this.cdr.detectChanges(); },
      error: ()  => { this.payloadsLoading = false; this.cdr.detectChanges(); }
    });
  }

  submitPayload(): void {
    if (this.payloadForm.invalid) return;
    this.payloadSubmitting = true;
    this.payloadGenericError = null;
    this.payloadViolations = [];
    this.payloadSuccess = null;

    this.payloadService.receivePayload(this.pipelineId, this.payloadForm.value).subscribe({
      next: res => {
        this.payloadSubmitting = false;
        this.payloadSuccess = res;
        this.payloadForm.reset();
        this.loadPayloads();
        this.cdr.detectChanges();
      },
      error: err => {
        this.payloadSubmitting = false;
        if (err.status === 422 && err.error?.violations) {
          this.payloadViolations = (err.error as PayloadValidationError).violations;
        } else {
          this.payloadGenericError = err?.error?.error ?? 'Submission failed.';
        }
        this.loadPayloads();
        this.cdr.detectChanges();
      }
    });
  }

  runPreview(): void {
    if (this.previewForm.invalid) return;
    this.previewLoading = true;
    this.previewResult = null;
    this.payloadService.previewValidation(this.pipelineId, this.previewForm.value).subscribe({
      next: res => { this.previewResult = res; this.previewLoading = false; this.cdr.detectChanges(); },
      error: ()  => { this.previewLoading = false; this.cdr.detectChanges(); }
    });
  }

  clearPreview(): void {
    this.previewResult = null;
    this.previewForm.reset({ format: 'JSON' });
    this.cdr.detectChanges();
  }

  violationLabel(errorType: string): string {
    const labels: Record<string, string> = {
      'MISSING_FIELD': 'Champ manquant', 'TYPE_MISMATCH': 'Type incorrect',
      'NULL_NOT_ALLOWED': 'Null interdit', 'INVALID_JSON': 'JSON invalide',
      'NOT_NULL': 'Valeur nulle', 'TYPE_NUMBER': 'Nombre attendu',
      'TYPE_DATE': 'Date invalide', 'REGEX_EMAIL': 'Email invalide',
      'REGEX_PHONE': 'Téléphone invalide', 'REGEX': 'Format invalide',
      'MIN_MAX_LENGTH': 'Longueur incorrecte'
    };
    return labels[errorType] ?? errorType;
  }
}