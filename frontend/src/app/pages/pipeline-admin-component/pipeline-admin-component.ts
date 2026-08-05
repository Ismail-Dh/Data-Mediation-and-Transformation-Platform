import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PipelineService } from '../../services/pipeline/pipeline-service';
import { Pipeline, PipelineDetails, providerNames } from '../../models/pipeline';

/**
 * Vue ADMIN — lecture seule de TOUS les pipelines (tous utilisateurs) + statistiques.
 * Aucune action de création, modification ou suppression n'est possible ici :
 * c'est un écran de consultation/supervision uniquement. La gestion des pipelines
 * (créer/éditer/supprimer) reste réservée à l'espace développeur.
 */
@Component({
  selector: 'app-pipeline-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pipeline-admin-component.html',
  styleUrls: ['./pipeline-admin-component.scss']
})
export class PipelineAdminComponent implements OnInit {

  pipelines: Pipeline[] = [];
  filtered:  Pipeline[] = [];

  searchQuery  = '';
  filterStatus = '';
  filterFormat = '';

  detailPipeline: Pipeline | null = null;
  detailLoading = false;
  detailError: string | null = null;
  pipelineDetails: PipelineDetails | null = null;

  /** Onglet actif dans le tiroir de détail : Schema / Validation / Mapping / Response */
  activeDetailTab: 'schema' | 'validation' | 'mapping' | 'response' = 'schema';

  readonly FORMATS  = ['JSON', 'XML', 'CSV', 'PLAIN_TEXT'];
  readonly STATUSES = ['DRAFT', 'CONFIGURED', 'VALIDATED'];

  // Helper multi-provider exposé au template
  readonly providerNames = providerNames;

  constructor(
    private pipelineService: PipelineService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadPipelines();
  }

  private loadPipelines(): void {
    this.pipelineService.getAll().subscribe(data => {
      this.pipelines = data;
      this.applyFilters();
      this.cdr.detectChanges();
    });
  }

  applyFilters(): void {
    let result = [...this.pipelines];
    const q = this.searchQuery.trim().toLowerCase();

    if (q) {
      result = result.filter(p =>
        p.name.toLowerCase().includes(q) ||
        p.createdBy.toLowerCase().includes(q) ||
        providerNames(p).toLowerCase().includes(q)
      );
    }
    if (this.filterStatus) {
      result = result.filter(p => p.status === this.filterStatus);
    }
    if (this.filterFormat) {
      result = result.filter(p =>
        p.inputFormat === this.filterFormat ||
        p.outputFormat === this.filterFormat
      );
    }
    this.filtered = result;
    this.cdr.detectChanges();
  }

  // ── Stats ──────────────────────────────────────────────────────────────────
  countByStatus(status: string): number {
    return this.pipelines.filter(p => p.status === status).length;
  }

  // ── Detail (lecture seule) ───────────────────────────────────────────────────
  openDetail(p: Pipeline): void {
    this.detailPipeline = p;
    this.activeDetailTab = 'schema';
    this.pipelineDetails = null;
    this.detailError = null;
    this.detailLoading = true;
    this.cdr.detectChanges();

    this.pipelineService.getFullDetails(p.id).subscribe({
      next: details => {
        this.pipelineDetails = details;
        this.detailLoading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.detailError = err?.error?.message ?? 'Failed to load pipeline details';
        this.detailLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  closeDetail(): void {
    this.detailPipeline = null;
    this.pipelineDetails = null;
    this.detailError = null;
    this.cdr.detectChanges();
  }

  setDetailTab(tab: 'schema' | 'validation' | 'mapping' | 'response'): void {
    this.activeDetailTab = tab;
    this.cdr.detectChanges();
  }
}