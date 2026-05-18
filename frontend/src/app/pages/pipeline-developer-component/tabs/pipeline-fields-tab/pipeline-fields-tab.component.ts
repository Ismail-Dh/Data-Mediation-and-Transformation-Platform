import { Component, Input, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { PipelineFieldService } from '../../../../services/pipelineField/pipeline-field-service';
import { PipelineFieldResponse } from '../../../../models/pipelineField';

@Component({
  selector: 'app-pipeline-fields-tab',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './pipeline-fields-tab.component.html',
  styleUrl: './pipeline-fields-tab.component.scss' 
})
export class PipelineFieldsTabComponent implements OnInit {

  @Input() pipelineId!: number;

  fields: PipelineFieldResponse[] = [];
  loading = false;
  editingFieldId: number | null = null;
  fieldForm!: FormGroup;

  readonly FIELD_TYPES = ['STRING', 'INTEGER', 'BOOLEAN', 'OBJECT', 'ARRAY'];

  constructor(
    private pipelineFieldService: PipelineFieldService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.fieldForm = this.fb.group({
      fieldPath: ['', Validators.required],
      fieldType: ['', Validators.required],
      required:  [false],
      nullable:  [false]
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
        this.fieldForm.reset({ required: false, nullable: false });
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
    this.fieldForm.reset({ required: false, nullable: false });
    this.cdr.detectChanges();
  }

  deleteField(id: number): void {
    this.pipelineFieldService.deleteField(this.pipelineId, id).subscribe(() => this.loadFields());
  }
}