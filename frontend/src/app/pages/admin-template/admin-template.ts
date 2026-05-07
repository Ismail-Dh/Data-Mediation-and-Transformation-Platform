import { Component, OnInit, signal, computed, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TemplateService } from '../../services/template/template-service';
import { TemplateResponse, TemplateType, TemplateStatus } from '../../models/template';

@Component({
  selector: 'app-admin-template',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, MatSnackBarModule],
  templateUrl: './admin-template.html',
  styleUrl:    './admin-template.scss'
})
export class AdminTemplate implements OnInit {

  templates   = signal<TemplateResponse[]>([]);
  loading     = signal(true);
  showForm    = signal(false);
  editingTpl  = signal<TemplateResponse | null>(null);
  detailTpl   = signal<TemplateResponse | null>(null);
  deletingTpl = signal<TemplateResponse | null>(null);

  activeType   = signal<TemplateType>('VALIDATION');
  searchQuery  = signal('');
  filterStatus = signal('');

  filtered = computed(() => {
    const q      = this.searchQuery().trim().toLowerCase();
    const status = this.filterStatus();
    return this.templates().filter(t => {
      const matchQ = !q || t.name.toLowerCase().includes(q) || (t.createdBy ?? '').toLowerCase().includes(q);
      const matchS = !status || t.status === status;
      return matchQ && matchS;
    });
  });

  readonly STATUSES: TemplateStatus[] = ['DRAFT', 'PUBLISHED', 'DISABLED'];
  readonly TYPES: TemplateType[]      = ['VALIDATION', 'MAPPING'];

  readonly RULE_TYPES  = ['NOT_NULL', 'TYPE_NUMBER', 'TYPE_DATE', 'REGEX_EMAIL', 'REGEX_PHONE', 'MIN_MAX_LENGTH', 'REGEX'];
  readonly MAPPING_TYPES = ['DIRECT', 'DATE_FORMAT', 'CONCAT', 'SPLIT', 'UPPERCASE', 'LOWERCASE'];

  // ── Formulaire principal ─────────────────────────────────────────────────
  metaForm!: FormGroup;   // name, description, type
  rulesArray!: FormArray; // pour VALIDATION
  mappingsArray!: FormArray; // pour MAPPING

  currentFormType = signal<TemplateType>('VALIDATION');

  constructor(
    private templateService: TemplateService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.buildForms();
    this.load();
  }

  private buildForms(): void {
    this.rulesArray = this.fb.array([]);
    this.mappingsArray = this.fb.array([]);

    this.metaForm = this.fb.group({
      name:        ['', [Validators.required, Validators.maxLength(200)]],
      description: [''],
      type:        ['VALIDATION', Validators.required]
    });

    // Synchronise le type affiché quand l'admin change le select
    this.metaForm.get('type')!.valueChanges.subscribe(val => {
      this.currentFormType.set(val);
      this.cdr.detectChanges();
    });
  }

  // ── Getters FormArray ─────────────────────────────────────────────────────
  get rules(): FormArray { return this.rulesArray; }
  get mappings(): FormArray { return this.mappingsArray; }

  ruleAt(i: number): FormGroup { return this.rulesArray.at(i) as FormGroup; }
  mappingAt(i: number): FormGroup { return this.mappingsArray.at(i) as FormGroup; }

  addRule(): void {
    this.rulesArray.push(this.fb.group({
      field:   ['', Validators.required],
      ruleType:['', Validators.required],
      pattern: ['']
    }));
    this.cdr.detectChanges();
  }

  removeRule(i: number): void {
    this.rulesArray.removeAt(i);
    this.cdr.detectChanges();
  }

  addMapping(): void {
    this.mappingsArray.push(this.fb.group({
      source:     ['', Validators.required],
      target:     ['', Validators.required],
      type:       ['DIRECT', Validators.required],
      expression: ['']
    }));
    this.cdr.detectChanges();
  }

  removeMapping(i: number): void {
    this.mappingsArray.removeAt(i);
    this.cdr.detectChanges();
  }

  // ── Data ──────────────────────────────────────────────────────────────────
  load(): void {
    this.loading.set(true);
    this.templateService.findAll(this.activeType()).subscribe({
      next: data => { this.templates.set(data); this.loading.set(false); this.cdr.detectChanges(); },
      error: ()   => { this.loading.set(false); this.notify('Error loading templates', true); this.cdr.detectChanges(); }
    });
  }

  switchType(type: TemplateType): void {
    this.activeType.set(type);
    this.searchQuery.set('');
    this.filterStatus.set('');
    this.load();
    this.cdr.detectChanges();
  }

  applyFilter(field: 'search' | 'status', value: string): void {
    if (field === 'search') this.searchQuery.set(value);
    else                    this.filterStatus.set(value);
    this.cdr.detectChanges();
  }

  countByStatus(status: TemplateStatus): number {
    return this.templates().filter(t => t.status === status).length;
  }

  // ── Modal ─────────────────────────────────────────────────────────────────
  openCreate(): void {
    this.editingTpl.set(null);
    this.metaForm.reset({ type: this.activeType() });
    this.currentFormType.set(this.activeType());
    this.rulesArray.clear();
    this.mappingsArray.clear();

    // Ajouter une ligne vide par défaut
    if (this.activeType() === 'VALIDATION') this.addRule();
    else                                    this.addMapping();

    this.showForm.set(true);
    this.cdr.detectChanges();
  }

  openEdit(t: TemplateResponse): void {
    this.editingTpl.set(t);
    this.metaForm.patchValue({ name: t.name, description: t.description, type: t.type });
    this.currentFormType.set(t.type);
    this.rulesArray.clear();
    this.mappingsArray.clear();

    if (t.type === 'VALIDATION') {
      const rules = (t.content['rules'] as any[]) ?? [];
      rules.forEach(r => this.rulesArray.push(this.fb.group({
        field:    [r.field    ?? '', Validators.required],
        ruleType: [r.ruleType ?? r.type ?? '', Validators.required],
        pattern:  [r.pattern  ?? '']
      })));
      if (rules.length === 0) this.addRule();
    } else {
      const mappings = (t.content['mappings'] as any[]) ?? [];
      mappings.forEach(m => this.mappingsArray.push(this.fb.group({
        source:     [m.source     ?? '', Validators.required],
        target:     [m.target     ?? '', Validators.required],
        type:       [m.type       ?? 'DIRECT', Validators.required],
        expression: [m.expression ?? '']
      })));
      if (mappings.length === 0) this.addMapping();
    }

    this.showForm.set(true);
    this.cdr.detectChanges();
  }

  closeModal(): void {
    this.showForm.set(false);
    this.editingTpl.set(null);
    this.cdr.detectChanges();
  }

  isFormValid(): boolean {
    if (this.metaForm.invalid) return false;
    if (this.currentFormType() === 'VALIDATION') return this.rulesArray.valid && this.rulesArray.length > 0;
    return this.mappingsArray.valid && this.mappingsArray.length > 0;
  }

  save(): void {
    if (!this.isFormValid()) return;

    const meta = this.metaForm.value;

    // Construction automatique du JSON content
    let content: Record<string, any>;
    if (this.currentFormType() === 'VALIDATION') {
      content = {
        rules: this.rulesArray.value.map((r: any) => {
          const rule: any = { field: r.field, ruleType: r.ruleType };
          if (r.pattern) rule.pattern = r.pattern;
          return rule;
        })
      };
    } else {
      content = {
        mappings: this.mappingsArray.value.map((m: any) => {
          const mapping: any = { source: m.source, target: m.target, type: m.type };
          if (m.expression) mapping.expression = m.expression;
          return mapping;
        })
      };
    }

    const req = { name: meta.name, description: meta.description || null, type: meta.type, content };
    const editing = this.editingTpl();

    if (editing) {
      this.templateService.update(editing.id, editing.type, req).subscribe({
        next: () => { this.notify('Template saved'); this.closeModal(); this.load(); this.cdr.detectChanges(); },
        error: err => this.handleError(err)
      });
    } else {
      this.templateService.create(req).subscribe({
        next: () => { this.notify('Template created'); this.closeModal(); this.load(); this.cdr.detectChanges(); },
        error: err => this.handleError(err)
      });
    }
  }

  publish(t: TemplateResponse): void {
    this.templateService.publish(t.id, t.type).subscribe({
      next: () => { this.notify('Template published'); this.load(); this.cdr.detectChanges(); },
      error: err => this.handleError(err)
    });
  }

  disable(t: TemplateResponse): void {
    this.templateService.disable(t.id, t.type).subscribe({
      next: () => { this.notify('Template disabled'); this.load(); this.cdr.detectChanges(); },
      error: err => this.handleError(err)
    });
  }

  openDetail(t: TemplateResponse): void  { this.detailTpl.set(t);   this.cdr.detectChanges(); }
  closeDetail(): void                    { this.detailTpl.set(null); this.cdr.detectChanges(); }
  askDelete(t: TemplateResponse): void   { this.deletingTpl.set(t);  this.cdr.detectChanges(); }
  cancelDelete(): void                   { this.deletingTpl.set(null); this.cdr.detectChanges(); }

  contentPreview(t: TemplateResponse): string {
    return JSON.stringify(t.content).slice(0, 80) + '…';
  }

  private handleError(err: any): void {
    const body = err.error;
    if      (err.status === 400) this.notify(`Error: ${Object.values(body).join(' | ')}`, true);
    else if (err.status === 404) this.notify('Not found', true);
    else                         this.notify('Server error', true);
    this.cdr.detectChanges();
  }

  private notify(msg: string, isError = false): void {
    this.snack.open(msg, 'OK', { duration: 4000, panelClass: isError ? ['snack-error'] : ['snack-success'] });
  }
}