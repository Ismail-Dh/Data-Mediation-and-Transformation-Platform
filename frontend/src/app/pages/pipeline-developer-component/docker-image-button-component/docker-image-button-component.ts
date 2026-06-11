import {
  ChangeDetectorRef, Component, Input,
  OnInit, OnChanges, SimpleChanges, OnDestroy
} from '@angular/core';
import {
  DockerImageService,
  DockerImageResponse,
  BuildCompleteEvent,
  BuildFailedEvent
} from '../../../services/image_docker/docker-image-service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-docker-image-button',
  imports: [CommonModule, FormsModule],
  templateUrl: './docker-image-button-component.html',
  styleUrls: ['./docker-image-button-component.scss']
})
export class DockerImageButtonComponent implements OnInit, OnChanges, OnDestroy {

  @Input() pipelineId!: number;
  @Input() pipelineStatus!: string;

  imageInfo:    DockerImageResponse | null = null;
  isGenerating  = false;
  errorMessage  = '';

  // SSE — logs de build en temps réel
  buildLogs:    string[] = [];
  showLogs      = false;
  private eventSource: EventSource | null = null;

  constructor(
    private dockerService: DockerImageService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    if (this.pipelineStatus === 'VALIDATED') {
      this.loadImageInfo();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['pipelineStatus']) {
      const newStatus = changes['pipelineStatus'].currentValue;
      const oldStatus = changes['pipelineStatus'].previousValue;
      if (newStatus !== oldStatus) {
        this.resetState();
        if (newStatus === 'VALIDATED') this.loadImageInfo();
        this.cdr.detectChanges();
      }
    }
    if (changes['pipelineId'] && !changes['pipelineId'].firstChange) {
      this.resetState();
      if (this.pipelineStatus === 'VALIDATED') this.loadImageInfo();
      this.cdr.detectChanges();
    }
  }

  ngOnDestroy(): void {
    this.closeEventSource();
  }

  // ── Getters ───────────────────────────────────────────────────────────────────

  get isValidated(): boolean { return this.pipelineStatus === 'VALIDATED'; }
  get imageExists(): boolean { return this.imageInfo?.status === 'SUCCESS'; }
  get isFailed():   boolean { return this.imageInfo?.status === 'FAILED'; }

  get imageSizeMb(): string {
    if (!this.imageInfo?.sizeBytes) return '';
    return (this.imageInfo.sizeBytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  // ── Chargement info image ─────────────────────────────────────────────────────

  loadImageInfo(): void {
    this.dockerService.getImageInfo(this.pipelineId).subscribe({
      next: (info) => { this.imageInfo = info; this.cdr.detectChanges(); },
      error: ()    => { this.imageInfo = null; this.cdr.detectChanges(); }
    });
  }

  // ── Génération via SSE ────────────────────────────────────────────────────────

  generateImage(): void {
    this.errorMessage  = '';
    this.buildLogs     = [];
    this.showLogs      = true;
    this.isGenerating  = true;
    this.imageInfo     = null;
    this.closeEventSource();
    this.cdr.detectChanges();

    this.eventSource = this.dockerService.streamBuildLogs(this.pipelineId, {

      onLog: (line: string) => {
        this.buildLogs.push(line);
        this.cdr.detectChanges();
      },

      onComplete: (event: BuildCompleteEvent) => {
        this.isGenerating = false;
        // Recharger les infos complètes de l'image depuis l'API
        this.loadImageInfo();
        this.cdr.detectChanges();
      },

      onFailed: (event: BuildFailedEvent) => {
        this.isGenerating = false;
        this.errorMessage = event.errorMessage || 'La génération a échoué.';
        this.imageInfo    = { ...this.imageInfo!, status: 'FAILED' };
        this.cdr.detectChanges();
      },

      onError: (_err: Event) => {
        this.isGenerating = false;
        this.errorMessage = 'Connexion SSE perdue. Vérifiez les logs serveur.';
        this.cdr.detectChanges();
      }
    });
  }

  toggleLogs(): void {
    this.showLogs = !this.showLogs;
    this.cdr.detectChanges();
  }

  downloadImage(): void {
    this.dockerService.downloadImage(this.pipelineId);
  }

  // ── Helpers privés ────────────────────────────────────────────────────────────

  private resetState(): void {
    this.closeEventSource();
    this.imageInfo    = null;
    this.errorMessage = '';
    this.buildLogs    = [];
    this.showLogs     = false;
    this.isGenerating = false;
  }

  private closeEventSource(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }
}