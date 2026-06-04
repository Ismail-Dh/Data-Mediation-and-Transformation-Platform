import {
  Component, Input, OnInit, OnDestroy, AfterViewInit,
  ChangeDetectorRef, ViewChild, ElementRef, NgZone
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Subject, fromEvent, forkJoin, of } from 'rxjs';
import { debounceTime, takeUntil, catchError } from 'rxjs/operators';

import { MappingRuleService } from '../../../../../services/mapping/mapping-rule-service';
import { MappingRuleResponse, MappingRuleRequest, MappingType } from '../../../../../models/mapping-rule';
import { PipelineFieldResponse, FieldType } from '../../../../../models/pipelineField';
import { PipelineFieldService } from '../../../../../services/pipelineField/pipeline-field-service';
import { environment } from '../../../../../environments/environment';

// ── Local types ───────────────────────────────────────────────────────────────

/** A field shown in either panel — pipeline field or manually declared provider field */
export interface DisplayField {
  fieldPath: string;
  fieldType: string;   // FieldType enum or free string for provider fields
  source: 'pipeline' | 'provider' | 'inferred';
}

export interface Connection {
  id: string;
  sourceField: string;
  targetField: string;
  mappingType: MappingType;
  expression: string;
  ruleId?: number;
  active: boolean;
}

export interface DragState {
  active: boolean;
  sourceField: string;
  sourceEl?: HTMLElement;
  mouseX: number;
  mouseY: number;
}

// ── Colour palette ────────────────────────────────────────────────────────────
const TYPE_COLORS: Record<MappingType, string> = {
  FIELD_PLACEMENT:  '#3b82f6',
  FORMAT_CHANGE:    '#f59e0b',
  VALUE_TRANSFORM:  '#8b5cf6',
  CALCULATED_FIELD: '#10b981',
  RESTRUCTURING:    '#ef4444',
};

const TYPE_LABELS: Record<MappingType, string> = {
  FIELD_PLACEMENT:  'Placement',
  FORMAT_CHANGE:    'Format',
  VALUE_TRANSFORM:  'Transform',
  CALCULATED_FIELD: 'Calculated',
  RESTRUCTURING:    'Restructuring',
};

@Component({
  selector: 'app-mapping-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './mapping-editor.html',
  styleUrl: './mapping-editor.scss',
})
export class MappingEditor implements OnInit, AfterViewInit, OnDestroy {

  /** The pipeline whose Consumer fields are the source */
  @Input() pipelineId!: number;

  /**
   * Optional: ID of a *second* pipeline that acts as the Provider.
   * If provided, its fields are loaded as target fields.
   * If null/undefined, target fields are inferred from existing mapping rules
   * and can be supplemented manually.
   */
  @Input() providerPipelineId: number | null = null;

  @ViewChild('editorCanvas') canvasRef!: ElementRef<HTMLDivElement>;
  @ViewChild('svgOverlay')   svgRef!: ElementRef<SVGElement>;

  // ── Data ────────────────────────────────────────────────────────────────────
  sourceFields: DisplayField[] = [];   // Consumer fields (from pipeline)
  targetFields: DisplayField[] = [];   // Provider fields (from providerPipeline OR inferred + manual)

  connections: Connection[] = [];
  loading = false;

  // ── Manual provider field form ───────────────────────────────────────────────
  showAddTargetField  = false;
  newTargetFieldPath  = '';
  newTargetFieldType: string = 'STRING';
  addTargetError      = '';

  readonly FIELD_TYPES: string[] = ['STRING', 'INTEGER', 'BOOLEAN', 'OBJECT', 'ARRAY', 'NUMBER', 'DATE'];

  // ── Drag state ───────────────────────────────────────────────────────────────
  drag: DragState = { active: false, sourceField: '', mouseX: 0, mouseY: 0 };
  hoveredTarget: string | null = null;

  // ── Config modal ─────────────────────────────────────────────────────────────
  configConn: Connection | null = null;
  configMappingType: MappingType = 'FIELD_PLACEMENT';
  configExpression = '';
  saving = false;
  saveError: string | null = null;

  readonly MAPPING_TYPES: MappingType[] = [
    'FIELD_PLACEMENT', 'FORMAT_CHANGE', 'VALUE_TRANSFORM',
    'CALCULATED_FIELD', 'RESTRUCTURING'
  ];

  private destroy$ = new Subject<void>();
  private save$    = new Subject<void>();

  constructor(
    private mappingService: MappingRuleService,
    private fieldService:   PipelineFieldService,
    private http:           HttpClient,
    private cdr:            ChangeDetectorRef,
    private zone:           NgZone,
  ) {}

  // ── Lifecycle ─────────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loading = true;
    this.loadData();
    this.save$.pipe(debounceTime(500), takeUntil(this.destroy$))
      .subscribe(() => this.persistMapping());
  }

  ngAfterViewInit(): void {
    this.zone.runOutsideAngular(() => {
      fromEvent<MouseEvent>(document, 'mousemove')
        .pipe(takeUntil(this.destroy$))
        .subscribe(e => this.onMouseMove(e));
      fromEvent<MouseEvent>(document, 'mouseup')
        .pipe(takeUntil(this.destroy$))
        .subscribe(e => this.onMouseUp(e));
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ── Load ──────────────────────────────────────────────────────────────────────

  private loadData(): void {
    // Always load Consumer fields (source pipeline) + existing rules
    const consumerFields$ = this.fieldService.getFields(this.pipelineId)
      .pipe(catchError(() => of([] as PipelineFieldResponse[])));

    const rules$ = this.mappingService.getAllRules(this.pipelineId)
      .pipe(catchError(() => of([] as MappingRuleResponse[])));

    // Provider fields: load from second pipeline if given, otherwise empty
    const providerFields$ = this.providerPipelineId
      ? this.fieldService.getFields(this.providerPipelineId)
          .pipe(catchError(() => of([] as PipelineFieldResponse[])))
      : of([] as PipelineFieldResponse[]);

    forkJoin([consumerFields$, providerFields$, rules$]).subscribe(
      ([consumerFields, providerFields, rules]) => {

        // Source panel = Consumer pipeline fields
        this.sourceFields = consumerFields.map(f => ({
          fieldPath: f.fieldPath,
          fieldType: f.fieldType,
          source: 'pipeline' as const,
        }));

        // Target panel = Provider pipeline fields (if available)
        // Otherwise infer from existing mapping rule targetFields
        if (providerFields.length > 0) {
          this.targetFields = providerFields.map(f => ({
            fieldPath: f.fieldPath,
            fieldType: f.fieldType,
            source: 'provider' as const,
          }));
        } else {
          this.targetFields = this.inferTargetFieldsFromRules(rules);
        }

        this.connections = rules.map(r => this.ruleToConnection(r));
        this.loading = false;
        this.cdr.detectChanges();
      }
    );
  }

  /**
   * When no provider pipeline is configured, reconstruct target fields
   * from the targetField strings stored in existing mapping rules.
   * These are shown as "inferred" so the user knows their origin.
   */
  private inferTargetFieldsFromRules(rules: MappingRuleResponse[]): DisplayField[] {
    const seen = new Set<string>();
    const fields: DisplayField[] = [];
    for (const r of rules) {
      if (r.targetField && r.targetField !== 'N/A' && !seen.has(r.targetField)) {
        seen.add(r.targetField);
        fields.push({ fieldPath: r.targetField, fieldType: '?', source: 'inferred' });
      }
    }
    return fields;
  }

  private ruleToConnection(r: MappingRuleResponse): Connection {
    return {
      id:          `${r.sourceField}::${r.targetField}`,
      sourceField: r.sourceField,
      targetField: r.targetField,
      mappingType: r.mappingType,
      expression:  r.expression ?? '',
      ruleId:      r.id,
      active:      r.active,
    };
  }

  // ── Manual target field management ───────────────────────────────────────────

  toggleAddTargetField(): void {
    this.showAddTargetField = !this.showAddTargetField;
    this.newTargetFieldPath = '';
    this.newTargetFieldType = 'STRING';
    this.addTargetError     = '';
    this.cdr.detectChanges();
  }

  addTargetField(): void {
    const path = this.newTargetFieldPath.trim();
    if (!path) { this.addTargetError = 'Field path is required.'; return; }
    if (this.targetFields.some(f => f.fieldPath === path)) {
      this.addTargetError = 'This field already exists.'; return;
    }
    this.targetFields = [
      ...this.targetFields,
      { fieldPath: path, fieldType: this.newTargetFieldType, source: 'provider' }
    ];
    this.showAddTargetField = false;
    this.addTargetError     = '';
    this.cdr.detectChanges();
  }

  removeTargetField(fieldPath: string): void {
    // Only remove if not connected
    if (this.connections.some(c => c.targetField === fieldPath && c.active)) return;
    this.targetFields = this.targetFields.filter(f => f.fieldPath !== fieldPath);
    this.cdr.detectChanges();
  }

  // ── Drag handlers ─────────────────────────────────────────────────────────────

  onFieldMouseDown(event: MouseEvent, field: DisplayField): void {
    event.preventDefault();
    this.drag = {
      active:      true,
      sourceField: field.fieldPath,
      sourceEl:    event.currentTarget as HTMLElement,
      mouseX:      event.clientX,
      mouseY:      event.clientY,
    };
    this.cdr.detectChanges();
  }

  private onMouseMove(e: MouseEvent): void {
    if (!this.drag.active) return;
    this.drag = { ...this.drag, mouseX: e.clientX, mouseY: e.clientY };
    this.zone.run(() => this.cdr.detectChanges());
  }

  private onMouseUp(_e: MouseEvent): void {
    if (!this.drag.active) return;
    if (this.hoveredTarget) {
      this.createConnection(this.drag.sourceField, this.hoveredTarget);
    }
    this.drag = { active: false, sourceField: '', mouseX: 0, mouseY: 0 };
    this.hoveredTarget = null;
    this.zone.run(() => this.cdr.detectChanges());
  }

  onTargetEnter(fieldPath: string): void {
    if (this.drag.active) this.hoveredTarget = fieldPath;
  }

  onTargetLeave(): void { this.hoveredTarget = null; }

  // ── Connection CRUD ──────────────────────────────────────────────────────────

  private createConnection(srcField: string, tgtField: string): void {
    const id = `${srcField}::${tgtField}`;
    if (this.connections.find(c => c.id === id)) return;

    const req: MappingRuleRequest = {
      sourceField: srcField,
      targetField: tgtField,
      mappingType: 'FIELD_PLACEMENT',
      expression:  '',
    };

    this.mappingService.createRule(this.pipelineId, req).subscribe({
      next: rule => {
        // If target field was not yet listed (drop onto a field typed in the ghost line),
        // add it as an inferred field so the panel stays consistent
        if (!this.targetFields.find(f => f.fieldPath === tgtField)) {
          this.targetFields = [
            ...this.targetFields,
            { fieldPath: tgtField, fieldType: '?', source: 'inferred' }
          ];
        }
        this.connections = [...this.connections, {
          id, sourceField: srcField, targetField: tgtField,
          mappingType: 'FIELD_PLACEMENT', expression: '', ruleId: rule.id, active: true,
        }];
        this.save$.next();
        this.cdr.detectChanges();
      }
    });
  }

  deleteConnection(conn: Connection): void {
    if (!conn.ruleId) return;
    this.mappingService.deleteRule(this.pipelineId, conn.ruleId).subscribe(() => {
      this.connections = this.connections.filter(c => c.id !== conn.id);
      this.save$.next();
      this.cdr.detectChanges();
    });
  }

  // ── Config modal ─────────────────────────────────────────────────────────────

  openConfig(conn: Connection): void {
    this.configConn        = conn;
    this.configMappingType = conn.mappingType;
    this.configExpression  = conn.expression;
    this.saveError         = null;
    this.cdr.detectChanges();
  }
/** True when the selected mapping type requires an expression */
get configNeedsExpression(): boolean {
  return ['VALUE_TRANSFORM', 'FORMAT_CHANGE', 'CALCULATED_FIELD', 'RESTRUCTURING']
    .includes(this.configMappingType);
}

/** Hint text matching the selected mapping type */
get configExpressionHint(): string {
  const hints: Record<string, string> = {
    VALUE_TRANSFORM:  'e.g. UPPERCASE · LOWERCASE · TRIM · CONCAT: :firstName:lastName · SPLIT:@:0 · REGEX_REPLACE:[^0-9]:',
    FORMAT_CHANGE:    'e.g. STRING_TO_INT · STRING_TO_DOUBLE · DATE_TO_UNIX · UNIX_TO_DATE · dd/MM/yyyy|yyyy-MM-dd',
    CALCULATED_FIELD: 'e.g. {price} * (1 + {tax}) · IF:amount:gt:1000:VIP:STD · SUM:items[].price · AVG:items[].qty',
    RESTRUCTURING:    'Leave empty to NEST · or type FLATTEN to flatten an object',
  };
  return hints[this.configMappingType] ?? '';
}
  closeConfig(): void { this.configConn = null; this.cdr.detectChanges(); }

  saveConfig(): void {
    if (!this.configConn?.ruleId) return;
    this.saving = true;
    this.saveError = null;

    const req: MappingRuleRequest = {
      sourceField: this.configConn.sourceField,
      targetField: this.configConn.targetField,
      mappingType: this.configMappingType,
      expression:  this.configExpression,
    };

    // Use PUT update — preserves active status, no delete+recreate
    this.mappingService.updateRule(this.pipelineId, this.configConn.ruleId, req).subscribe({
      next: rule => {
        this.connections = this.connections.map(c =>
          c.id === this.configConn!.id
            ? { ...c, ruleId: rule.id, mappingType: this.configMappingType, expression: this.configExpression }
            : c
        );
        this.saving    = false;
        this.configConn = null;
        this.save$.next();
        this.cdr.detectChanges();
      },
      error: err => {
        this.saveError = err?.error?.message ?? 'Save failed';
        this.saving    = false;
        this.cdr.detectChanges();
      }
    });
  }

  // ── SVG helpers ───────────────────────────────────────────────────────────────

  getConnectionPath(conn: Connection): string {
    return this.bezier('src-' + conn.sourceField, 'tgt-' + conn.targetField);
  }

  getDragLinePath(): string {
    if (!this.drag.active || !this.drag.sourceEl || !this.svgRef) return '';
    const svgRect = this.svgRef.nativeElement.getBoundingClientRect();
    const srcRect = this.drag.sourceEl.getBoundingClientRect();
    const x1 = srcRect.right  - svgRect.left;
    const y1 = srcRect.top + srcRect.height / 2 - svgRect.top;
    const x2 = this.drag.mouseX - svgRect.left;
    const y2 = this.drag.mouseY - svgRect.top;
    const cp = Math.abs(x2 - x1) * 0.45;
    return `M${x1},${y1} C${x1 + cp},${y1} ${x2 - cp},${y2} ${x2},${y2}`;
  }

  getConnectionMidpoint(conn: Connection): { x: number; y: number } | null {
    if (!this.svgRef || !this.canvasRef) return null;
    const svgRect   = this.svgRef.nativeElement.getBoundingClientRect();
    const canvas    = this.canvasRef.nativeElement;
    const srcEl = canvas.querySelector(`[data-field-id="src-${conn.sourceField}"]`) as HTMLElement;
    const tgtEl = canvas.querySelector(`[data-field-id="tgt-${conn.targetField}"]`) as HTMLElement;
    if (!srcEl || !tgtEl) return null;
    const s = srcEl.getBoundingClientRect();
    const t = tgtEl.getBoundingClientRect();
    return {
      x: (s.right + t.left) / 2 - svgRect.left,
      y: (s.top + s.height / 2 + t.top + t.height / 2) / 2 - svgRect.top,
    };
  }

  private bezier(srcDataId: string, tgtDataId: string): string {
    if (!this.svgRef || !this.canvasRef) return '';
    const svgRect = this.svgRef.nativeElement.getBoundingClientRect();
    const canvas  = this.canvasRef.nativeElement;
    const srcEl = canvas.querySelector(`[data-field-id="${srcDataId}"]`) as HTMLElement;
    const tgtEl = canvas.querySelector(`[data-field-id="${tgtDataId}"]`) as HTMLElement;
    if (!srcEl || !tgtEl) return '';
    const s = srcEl.getBoundingClientRect();
    const t = tgtEl.getBoundingClientRect();
    const x1 = s.right  - svgRect.left, y1 = s.top + s.height / 2 - svgRect.top;
    const x2 = t.left   - svgRect.left, y2 = t.top + t.height / 2 - svgRect.top;
    const cp = Math.abs(x2 - x1) * 0.45;
    return `M${x1},${y1} C${x1 + cp},${y1} ${x2 - cp},${y2} ${x2},${y2}`;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────

  connColor(conn: Connection):    string { return TYPE_COLORS[conn.mappingType] ?? '#94a3b8'; }
  typeLabel(type: MappingType):   string { return TYPE_LABELS[type] ?? type; }
  typeColor(type: MappingType):   string { return TYPE_COLORS[type] ?? '#94a3b8'; }

  isSourceConnected(fp: string):  boolean { return this.connections.some(c => c.sourceField === fp && c.active); }
  isTargetConnected(fp: string):  boolean { return this.connections.some(c => c.targetField === fp && c.active); }

  canRemoveTarget(fp: string):    boolean { return !this.connections.some(c => c.targetField === fp && c.active); }

  private persistMapping(): void {
    const payload = {
      rules: this.connections.filter(c => c.active).map(c => ({
        sourceField: c.sourceField, targetField: c.targetField,
        mappingType: c.mappingType, expression:  c.expression,
      }))
    };
    this.http.put(`${environment.apiUrl}/api/pipelines/${this.pipelineId}/mapping`, payload)
      .subscribe({ error: err => console.warn('PUT /mapping failed', err) });
  }

  trackConn(_: number, c: Connection):  string { return c.id; }
  trackField(_: number, f: DisplayField): string { return f.fieldPath; }
}