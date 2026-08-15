import { Component, Input, OnInit, ChangeDetectorRef, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { PipelineFieldService } from '../../../../services/pipelineField/pipeline-field-service';
import { PipelineFieldResponse, PipelineFieldImportResponse } from '../../../../models/pipelineField';

@Component({
  selector: 'app-pipeline-fields-tab',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './pipeline-fields-tab.component.html',
  styleUrl: './pipeline-fields-tab.component.scss' 
})
export class PipelineFieldsTabComponent implements OnInit {

  @Input() pipelineId!: number;

  @ViewChild('jsonFileInput') jsonFileInput!: ElementRef<HTMLInputElement>;

  fields: PipelineFieldResponse[] = [];
  loading = false;
  editingFieldId: number | null = null;
  fieldForm!: FormGroup;

  // ── Import de schéma via fichier JSON ────────────────────────────────────
  importing = false;
  importResult: PipelineFieldImportResponse | null = null;
  importErrorMessage: string | null = null;

readonly FIELD_TYPES = [
  'STRING',
  'INTEGER',
  'NUMBER',
  'BOOLEAN',
  'OBJECT',
  'ARRAY'
];
  constructor(
    private pipelineFieldService: PipelineFieldService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.fieldForm = this.fb.group({
      fieldPath: ['', Validators.required],
      fieldType: ['', Validators.required],
      required:  [false]
      // nullable is auto-computed by the backend as !required
    });
    this.loadFields();
  }

  loadFields(): void {
    this.loading = true;
    this.pipelineFieldService.getFields(this.pipelineId).subscribe({
      next: data => { this.fields = data; this.loading = false; this.cdr.detectChanges(); },
      error: ()  => { this.loading = false; this.cdr.detectChanges(); }
    });
  }

  saveField(): void {
    if (this.fieldForm.invalid) return;
    const val = this.fieldForm.value;
    if (this.editingFieldId !== null) {
      this.pipelineFieldService.updateField(this.pipelineId, this.editingFieldId, val).subscribe(() => {
        this.cancelEdit(); this.loadFields();
      });
    } else {
      this.pipelineFieldService.addField(this.pipelineId, val).subscribe(() => {
        this.fieldForm.reset({ required: false });
        this.loadFields();
      });
    }
  }

  openEdit(f: PipelineFieldResponse): void {
    this.editingFieldId = f.id;
    this.fieldForm.patchValue(f);
    this.cdr.detectChanges();
  }

  cancelEdit(): void {
    this.editingFieldId = null;
    this.fieldForm.reset({ required: false });
    this.cdr.detectChanges();
  }

  deleteField(id: number): void {
    this.pipelineFieldService.deleteField(this.pipelineId, id).subscribe(() => this.loadFields());
  }

  // ── Import de schéma via fichier JSON ────────────────────────────────────

  /** Ouvre le sélecteur de fichier natif (input[type=file] caché). */
  triggerJsonImport(): void {
    this.importResult = null;
    this.importErrorMessage = null;
    this.jsonFileInput.nativeElement.click();
  }

  /**
   * Envoie le fichier JSON sélectionné à l'API pour créer en masse les
   * champs du schéma. Format attendu : un tableau
   * [{ fieldPath, fieldType, required }, ...].
   */
  onJsonFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = ''; // permet de resélectionner le même fichier plus tard

    if (!file) return;

    if (!file.name.toLowerCase().endsWith('.json')) {
      this.importErrorMessage = 'Merci de sélectionner un fichier .json';
      this.cdr.detectChanges();
      return;
    }

    this.importing = true;
    this.importResult = null;
    this.importErrorMessage = null;

    this.pipelineFieldService.importFieldsFromJson(this.pipelineId, file).subscribe({
      next: (res) => {
        this.importResult = res;
        this.importing = false;
        this.loadFields();
      },
      error: (err: HttpErrorResponse) => {
        this.importErrorMessage = err.error?.error ?? "Échec de l'import du fichier JSON.";
        this.importing = false;
        this.cdr.detectChanges();
      }
    });
  }

  dismissImportResult(): void {
    this.importResult = null;
    this.importErrorMessage = null;
  }
}