import { Component, OnInit, signal, computed, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TemplateService } from '../../services/template/template-service';
import { TemplateResponse, TemplateType } from '../../models/template';

@Component({
  selector: 'app-developer-template',
  standalone: true,
  imports: [CommonModule, FormsModule, MatSnackBarModule],
  templateUrl: './developer-template.html',
  styleUrl:    './developer-template.scss'
})
export class DeveloperTemplate implements OnInit {

  templates    = signal<TemplateResponse[]>([]);
  loading      = signal(true);
  detailTpl    = signal<TemplateResponse | null>(null);
  activeType   = signal<TemplateType>('VALIDATION');
  searchQuery  = signal('');

  filtered = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    return this.templates().filter(t =>
      !q || t.name.toLowerCase().includes(q) || (t.description ?? '').toLowerCase().includes(q)
    );
  });

  readonly TYPES: TemplateType[] = ['VALIDATION', 'MAPPING'];

  constructor(
    private templateService: TemplateService,
    private cdr: ChangeDetectorRef,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.templateService.findPublished(this.activeType()).subscribe({
      next: data => { this.templates.set(data); this.loading.set(false); this.cdr.detectChanges(); },
      error: ()   => { this.loading.set(false); this.snack.open('Error loading templates', 'OK', { duration: 3000 }); this.cdr.detectChanges(); }
    });
  }

  switchType(type: TemplateType): void {
    this.activeType.set(type);
    this.searchQuery.set('');
    this.load();
    this.cdr.detectChanges();
  }

  applyFilter(value: string): void {
    this.searchQuery.set(value);
    this.cdr.detectChanges();
  }

  openDetail(t: TemplateResponse): void  { this.detailTpl.set(t);   this.cdr.detectChanges(); }
  closeDetail(): void                    { this.detailTpl.set(null); this.cdr.detectChanges(); }

  contentPreview(t: TemplateResponse): string {
    return JSON.stringify(t.content).slice(0, 80) + '…';
  }
}