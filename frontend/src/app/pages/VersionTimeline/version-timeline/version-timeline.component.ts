import { Component, Input, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DockerImageService, BuildLogEntryResponse } from '../../../services/image_docker/docker-image-service';

@Component({
  selector: 'app-version-timeline',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './version-timeline.component.html',
  styleUrls: ['./version-timeline.component.scss']
})
export class VersionTimelineComponent implements OnInit {

  @Input() pipelineId!: number;
  @Input() pipelineStatus!: string;

  versions:  BuildLogEntryResponse[] = [];
  loading    = false;
  showAll    = false;

  constructor(
    private dockerService: DockerImageService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    if (this.pipelineStatus === 'VALIDATED') {
      this.load();
    }
  }

  load(): void {
    this.loading = true;
    this.dockerService.getVersionHistory(this.pipelineId).subscribe({
      next: versions => {
        this.versions = versions;
        this.loading  = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  get displayed(): BuildLogEntryResponse[] {
    return this.showAll ? this.versions : this.versions.slice(0, 5);
  }

  toggleShowAll(): void { this.showAll = !this.showAll; }

  download(v: BuildLogEntryResponse): void {
    this.dockerService.downloadImage(this.pipelineId);
  }

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString();
  }

  formatDuration(sec: number | null): string {
    if (sec === null) return '—';
    if (sec < 60) return `${sec}s`;
    return `${Math.floor(sec / 60)}m ${sec % 60}s`;
  }

  statusIcon(status: string): string {
    return { SUCCESS: '✅', FAILED: '❌', BUILDING: '⏳', PENDING: '🕐' }[status] ?? '—';
  }
}