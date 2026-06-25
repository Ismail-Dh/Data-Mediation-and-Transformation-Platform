import {
  ChangeDetectorRef, Component, Input,
  OnInit, OnChanges, SimpleChanges, OnDestroy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  DockerImageService,
  DockerImageResponse,
  BuildCompleteEvent,
  BuildFailedEvent
} from '../../../services/image_docker/docker-image-service';
import {
  RegistryService,
  PushImageResponse
} from '../../../services/registry/registry-service';
import { Registry } from '../../../models/registry.model';

@Component({
  selector: 'app-docker-image-button',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './docker-image-button-component.html',
  styleUrls: ['./docker-image-button-component.scss']
})
export class DockerImageButtonComponent implements OnInit, OnChanges, OnDestroy {

  @Input() pipelineId!:     number;
  @Input() pipelineStatus!: string;

  imageInfo:    DockerImageResponse | null = null;
  isGenerating  = false;
  errorMessage  = '';

  buildLogs:    string[] = [];
  showLogs      = false;
  private eventSource: EventSource | null = null;

  // ── Push ──────────────────────────────────────────────────────────────────
  registries:       Registry[]          = [];
  selectedRegistry: number | null       = null;
  isPushing         = false;
  pushResult:       PushImageResponse | null = null;
  pushError         = '';
  showPushPanel     = false;

  constructor(
    private dockerService:   DockerImageService,
    private registryService: RegistryService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    if (this.pipelineStatus === 'VALIDATED') {
      this.loadImageInfo();
      this.loadRegistries();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['pipelineStatus']) {
      const newStatus = changes['pipelineStatus'].currentValue;
      const oldStatus = changes['pipelineStatus'].previousValue;
      if (newStatus !== oldStatus) {
        this.resetState();
        if (newStatus === 'VALIDATED') {
          this.loadImageInfo();
          this.loadRegistries();
        }
        this.cdr.detectChanges();
      }
    }
    if (changes['pipelineId'] && !changes['pipelineId'].firstChange) {
      this.resetState();
      if (this.pipelineStatus === 'VALIDATED') {
        this.loadImageInfo();
        this.loadRegistries();
      }
      this.cdr.detectChanges();
    }
  }

  ngOnDestroy(): void { this.closeEventSource(); }

  // ── Getters ───────────────────────────────────────────────────────────────

  get isValidated(): boolean { return this.pipelineStatus === 'VALIDATED'; }
  get imageExists(): boolean { return this.imageInfo?.status === 'SUCCESS'; }
  get isFailed():   boolean { return this.imageInfo?.status === 'FAILED'; }

  get imageSizeMb(): string {
    if (!this.imageInfo?.sizeBytes) return '';
    return (this.imageInfo.sizeBytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  // ── Image info ────────────────────────────────────────────────────────────

  loadImageInfo(): void {
    this.dockerService.getImageInfo(this.pipelineId).subscribe({
      next: info => { this.imageInfo = info; this.cdr.detectChanges(); },
      error: ()  => { this.imageInfo = null; this.cdr.detectChanges(); }
    });
  }

  // ── Registries ────────────────────────────────────────────────────────────

  loadRegistries(): void {
    this.registryService.getAll().subscribe({
      next: list => {
        this.registries = list;
        if (list.length > 0) this.selectedRegistry = list[0].id;
        this.cdr.detectChanges();
      },
      error: () => this.cdr.detectChanges()
    });
  }

  togglePushPanel(): void {
    this.showPushPanel = !this.showPushPanel;
    this.pushResult    = null;
    this.pushError     = '';
    this.cdr.detectChanges();
  }

  pushImage(): void {
    if (!this.selectedRegistry) return;
    this.isPushing  = true;
    this.pushResult = null;
    this.pushError  = '';
    this.cdr.detectChanges();

    this.registryService.pushImage(this.pipelineId, this.selectedRegistry).subscribe({
      next: result => {
        this.pushResult = result;
        this.isPushing  = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.pushError = err?.error?.error ?? 'Push failed.';
        this.isPushing = false;
        this.cdr.detectChanges();
      }
    });
  }

  // ── Generate ──────────────────────────────────────────────────────────────

  generateImage(): void {
    this.errorMessage = '';
    this.buildLogs    = [];
    this.showLogs     = true;
    this.isGenerating = true;
    this.imageInfo    = null;
    this.pushResult   = null;
    this.closeEventSource();
    this.cdr.detectChanges();

    this.eventSource = this.dockerService.streamBuildLogs(this.pipelineId, {
      onLog: line => { this.buildLogs.push(line); this.cdr.detectChanges(); },
      onComplete: () => { this.isGenerating = false; this.loadImageInfo(); this.cdr.detectChanges(); },
      onFailed: event => {
        this.isGenerating = false;
        this.errorMessage = event.errorMessage || 'Generation failed.';
        this.imageInfo    = { ...this.imageInfo!, status: 'FAILED' };
        this.cdr.detectChanges();
      },
      onError: () => {
        this.isGenerating = false;
        this.errorMessage = 'SSE connection lost.';
        this.cdr.detectChanges();
      }
    });
  }

  toggleLogs(): void { this.showLogs = !this.showLogs; this.cdr.detectChanges(); }
  downloadImage(): void { this.dockerService.downloadImage(this.pipelineId); }

  private resetState(): void {
    this.closeEventSource();
    this.imageInfo       = null;
    this.errorMessage    = '';
    this.buildLogs       = [];
    this.showLogs        = false;
    this.isGenerating    = false;
    this.pushResult      = null;
    this.pushError       = '';
    this.showPushPanel   = false;
    this.selectedRegistry = null;
  }

  private closeEventSource(): void {
    if (this.eventSource) { this.eventSource.close(); this.eventSource = null; }
  }
}