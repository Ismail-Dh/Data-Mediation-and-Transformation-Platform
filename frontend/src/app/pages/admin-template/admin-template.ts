import { Component, OnInit, signal, computed, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
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

  templates     = signal<TemplateResponse[]>([]);
  loading       = signal(true);
  showForm      = signal(false);
  editingTpl    = signal<TemplateResponse | null>(null);
  detailTpl     = signal<TemplateResponse | null>(null);
  deletingTpl   = signal<TemplateResponse | null>(null);

  // ── Filtres ──────────────────────────────────────────────────────────────
  activeType    = signal<TemplateType>('VALIDATION');
  searchQuery   = signal('');
  filterStatus  = signal('');

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

  form!: FormGroup;

  constructor(
    private templateService: TemplateService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.buildForm();
    this.load();
  }

  private buildForm(): void {
    this.form = this.fb.group({
      name:        ['', [Validators.required, Validators.maxLength(200)]],
      description: [''],
      type:        ['VALIDATION', Validators.required],
      content:     ['', Validators.required]
    });
  }

  // ── Data ────────────────────────────────────────────────────────────────
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

  // ── Stats ────────────────────────────────────────────────────────────────
  countByStatus(status: TemplateStatus): number {
    return this.templates().filter(t => t.status === status).length;
  }

  // ── Modal ────────────────────────────────────────────────────────────────
  openCreate(): void {
    this.editingTpl.set(null);
    this.form.reset({ type: this.activeType(), content: '' });
    this.showForm.set(true);
    this.cdr.detectChanges();
  }

  openEdit(t: TemplateResponse): void {
    this.editingTpl.set(t);
    this.form.patchValue({
      name:        t.name,
      description: t.description,
      type:        t.type,
      content:     JSON.stringify(t.content, null, 2)
    });
    this.showForm.set(true);
    this.cdr.detectChanges();
  }

  closeModal(): void {
    this.showForm.set(false);
    this.editingTpl.set(null);
    this.cdr.detectChanges();
  }

  save(): void {
    if (this.form.invalid) return;
    const val = this.form.value;
    let content: Record<string, any>;
    try { content = JSON.parse(val.content); }
    catch { this.notify('Content must be valid JSON', true); return; }

    const req = { name: val.name, description: val.description || null, type: val.type, content };
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

  // ── Lifecycle actions ────────────────────────────────────────────────────
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

  // ── Detail ───────────────────────────────────────────────────────────────
  openDetail(t: TemplateResponse): void  { this.detailTpl.set(t);   this.cdr.detectChanges(); }
  closeDetail(): void                    { this.detailTpl.set(null); this.cdr.detectChanges(); }

  // ── Delete ───────────────────────────────────────────────────────────────
  askDelete(t: TemplateResponse): void { this.deletingTpl.set(t);   this.cdr.detectChanges(); }
  cancelDelete(): void                 { this.deletingTpl.set(null); this.cdr.detectChanges(); }

  // ── Helpers ──────────────────────────────────────────────────────────────
  contentPreview(t: TemplateResponse): string {
    return JSON.stringify(t.content).slice(0, 80) + '…';
  }

  private handleError(err: any): void {
    const body = err.error;
    if      (err.status === 400) this.notify(`Error: ${Object.values(body).join(' | ')}`, true);
    else if (err.status === 404) this.notify(`Not found`, true);
    else                         this.notify('Server error', true);
    this.cdr.detectChanges();
  }

  private notify(msg: string, isError = false): void {
    this.snack.open(msg, 'OK', { duration: 4000, panelClass: isError ? ['snack-error'] : ['snack-success'] });
  }
}