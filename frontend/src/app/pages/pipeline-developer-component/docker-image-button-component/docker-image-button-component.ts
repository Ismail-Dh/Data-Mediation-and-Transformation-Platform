import { 
  ChangeDetectorRef, Component, Input, 
  OnInit, OnChanges, SimpleChanges, OnDestroy  // ← ajouter
} from '@angular/core';
import { DockerImageService, DockerImageResponse } from '../../../services/image_docker/docker-image-service';
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

  imageInfo: DockerImageResponse | null = null;
  isGenerating = false;
  errorMessage = '';
  private pollInterval: any = null;  // ← garder référence pour cleanup

  constructor(
    private dockerService: DockerImageService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    if (this.pipelineStatus === 'VALIDATED') {
      this.loadImageInfo();
    }
  }

  // ← AJOUT : réagir aux changements d'inputs
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['pipelineStatus']) {
      const newStatus = changes['pipelineStatus'].currentValue;
      const oldStatus = changes['pipelineStatus'].previousValue;

      if (newStatus !== oldStatus) {
        this.imageInfo = null;
        this.errorMessage = '';
        if (newStatus === 'VALIDATED') {
          this.loadImageInfo();
        }
        this.cdr.detectChanges();
      }
    }

    // Si pipelineId change aussi (navigation entre pipelines)
    if (changes['pipelineId'] && !changes['pipelineId'].firstChange) {
      this.imageInfo = null;
      this.errorMessage = '';
      this.stopPolling();
      if (this.pipelineStatus === 'VALIDATED') {
        this.loadImageInfo();
      }
      this.cdr.detectChanges();
    }
  }

  // ← AJOUT : cleanup du polling quand composant détruit
  ngOnDestroy(): void {
    this.stopPolling();
  }

  private stopPolling(): void {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }

  get isValidated(): boolean {
    return this.pipelineStatus === 'VALIDATED';
  }

  get imageExists(): boolean {
    return this.imageInfo?.status === 'SUCCESS';
  }

  get isFailed(): boolean {
    return this.imageInfo?.status === 'FAILED';
  }

  get imageSizeMb(): string {
    if (!this.imageInfo?.sizeBytes) return '';
    return (this.imageInfo.sizeBytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  loadImageInfo(): void {
    this.dockerService.getImageInfo(this.pipelineId).subscribe({
      next: (info) => {
        this.imageInfo = info;
        this.cdr.detectChanges();  // ← forcer la détection
      },
      error: () => {
        this.imageInfo = null;
        this.cdr.detectChanges();
      }
    });
  }

  generateImage(): void {
    this.errorMessage = '';
    this.isGenerating = true;
    this.cdr.detectChanges();

    this.dockerService.generateImage(this.pipelineId).subscribe({
      next: () => {
        this.isGenerating = false;
        this.pollImageStatus();
      },
      error: (err) => {
        this.isGenerating = false;
        this.errorMessage = err?.error?.message || 'Erreur lors de la génération.';
        this.cdr.detectChanges();
      }
    });
  }

  private pollImageStatus(): void {
    this.stopPolling();  // ← éviter doublons
    this.pollInterval = setInterval(() => {
      this.dockerService.getImageInfo(this.pipelineId).subscribe({
        next: (info) => {
          this.imageInfo = info;
          this.cdr.detectChanges();  // ← forcer mise à jour UI
          if (info.status === 'SUCCESS' || info.status === 'FAILED') {
            this.stopPolling();
            if (info.status === 'FAILED') {
              this.errorMessage = 'La génération a échoué. Vérifiez les logs.';
              this.cdr.detectChanges();
            }
          }
        },
        error: () => this.stopPolling()
      });
    }, 2000);
  }

  downloadImage(): void {
    this.dockerService.downloadImage(this.pipelineId);
  }
}