import { Component, Input, OnInit } from '@angular/core';
import { DockerImageService, DockerImageResponse } from '../../../services/image_docker/docker-image-service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-docker-image-button',
  imports: [CommonModule, FormsModule],

  templateUrl: './docker-image-button-component.html',
  styleUrls: ['./docker-image-button-component.scss']
})
export class DockerImageButtonComponent implements OnInit {

  @Input() pipelineId!: number;
  @Input() pipelineStatus!: string; // must be 'VALIDATED' to enable

  imageInfo: DockerImageResponse | null = null;
  isGenerating = false;
  errorMessage = '';

  constructor(private dockerService: DockerImageService) {}

  ngOnInit(): void {
    // On charge l'état de l'image si le pipeline est déjà validé
    if (this.pipelineStatus === 'VALIDATED') {
      this.loadImageInfo();
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
      next: (info) => this.imageInfo = info,
      error: () => this.imageInfo = null // pas encore d'image — normal
    });
  }

  generateImage(): void {
    this.errorMessage = '';
    this.isGenerating = true;

    this.dockerService.generateImage(this.pipelineId).subscribe({
      next: (resp) => {
        this.isGenerating = false;
        // Polling de l'état toutes les 2s jusqu'à SUCCESS ou FAILED
        this.pollImageStatus();
      },
      error: (err) => {
        this.isGenerating = false;
        this.errorMessage = err?.error?.message || 'Erreur lors de la génération.';
      }
    });
  }

  private pollImageStatus(): void {
    const interval = setInterval(() => {
      this.dockerService.getImageInfo(this.pipelineId).subscribe({
        next: (info) => {
          this.imageInfo = info;
          if (info.status === 'SUCCESS' || info.status === 'FAILED') {
            clearInterval(interval);
            if (info.status === 'FAILED') {
              this.errorMessage = 'La génération a échoué. Vérifiez les logs.';
            }
          }
        },
        error: () => clearInterval(interval)
      });
    }, 2000);
  }

  downloadImage(): void {
    this.dockerService.downloadImage(this.pipelineId);
  }
}
