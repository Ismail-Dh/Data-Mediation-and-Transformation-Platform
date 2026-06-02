
import {
  Component, Input, OnInit, OnDestroy, AfterViewInit,
  ChangeDetectorRef, ViewChild, ElementRef, NgZone
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, takeUntil, distinctUntilChanged } from 'rxjs/operators';

import { MappingRuleService } from '../../../../../services/mapping/mapping-rule-service';
import { MappingRuleRequest, MappingRuleResponse, MappingType } from '../../../../../models/mapping-rule';

// ── Monaco type shims (loaded via CDN, not via npm) ──────────────────────────
declare const monaco: any;

// ── JSON schema for a mapping rule array ─────────────────────────────────────
const MAPPING_JSON_SCHEMA = {
  $schema: 'http://json-schema.org/draft-07/schema#',
  type: 'array',
  items: {
    type: 'object',
    required: ['sourceField', 'targetField', 'mappingType'],
    additionalProperties: false,
    properties: {
      sourceField:  { type: 'string', minLength: 1 },
      targetField:  { type: 'string', minLength: 1 },
      mappingType: {
        type: 'string',
        enum: ['FIELD_PLACEMENT', 'FORMAT_CHANGE', 'VALUE_TRANSFORM', 'CALCULATED_FIELD', 'RESTRUCTURING']
      },
      expression: { type: 'string' },
    }
  }
};

export type EditorMode = 'visual' | 'json';

export interface MappingEntry {
  sourceField: string;
  targetField: string;
  mappingType: MappingType;
  expression:  string;
}

@Component({
  selector: 'app-mapping-json-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './mapping-json-editor.html',
  styleUrl: './mapping-json-editor.scss',
})
export class MappingJsonEditor implements OnInit, AfterViewInit, OnDestroy {

  @Input() pipelineId!: number;
  @ViewChild('monacoContainer') monacoContainerRef!: ElementRef<HTMLDivElement>;

  mode: EditorMode = 'visual';

  // ── Data ────────────────────────────────────────────────────────────────────
  rules: MappingRuleResponse[] = [];
  loading = false;

  // ── Monaco state ────────────────────────────────────────────────────────────
  private monacoEditor: any = null;
  private monacoLoaded = false;
  jsonText = '';

  // ── Validation ──────────────────────────────────────────────────────────────
  jsonErrors: string[] = [];
  jsonValid  = true;
  jsonDirty  = false;

  // ── Save state ──────────────────────────────────────────────────────────────
  saving     = false;
  saveStatus: 'idle' | 'saved' | 'error' = 'idle';

  private destroy$  = new Subject<void>();
  private jsonEdit$ = new Subject<string>();

  constructor(
    private mappingService: MappingRuleService,
    private cdr:            ChangeDetectorRef,
    private zone:           NgZone,
  ) {}

  // ── Lifecycle ───────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadRules();

    // Debounced validation while typing JSON
    this.jsonEdit$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(text => {
      this.validateJson(text);
      this.jsonDirty = true;
      this.cdr.detectChanges();
    });
  }

  ngAfterViewInit(): void {
    this.ensureMonacoLoaded();
  }

  ngOnDestroy(): void {
    this.monacoEditor?.dispose();
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ── Load rules ──────────────────────────────────────────────────────────────

  private loadRules(): void {
    this.loading = true;
    this.mappingService.getAllRules(this.pipelineId).subscribe({
      next: data => {
        this.rules = data;
        this.refreshJsonFromRules();
        this.loading = false;
        this.cdr.detectChanges();
        if (this.mode === 'json') this.pushJsonToMonaco();
      },
      error: () => { this.loading = false; this.cdr.detectChanges(); }
    });
  }

  // ── Mode toggle ─────────────────────────────────────────────────────────────

  switchMode(target: EditorMode): void {
    if (target === this.mode) return;

    if (target === 'json') {
      this.refreshJsonFromRules();
      this.mode = 'json';
      this.cdr.detectChanges();
      setTimeout(() => { this.initMonaco(); }, 50);
    } else {
      // JSON → Visual: parse + apply
      if (!this.jsonValid) return;
      this.applyJsonToRules(this.jsonText);
      this.mode = 'visual';
      this.cdr.detectChanges();
    }
  }

  // ── JSON ↔ Rules sync ───────────────────────────────────────────────────────

  private refreshJsonFromRules(): void {
    const entries: MappingEntry[] = this.rules
      .filter(r => r.active)
      .map(r => ({
        sourceField: r.sourceField,
        targetField: r.targetField,
        mappingType: r.mappingType,
        expression:  r.expression ?? '',
      }));
    this.jsonText  = JSON.stringify(entries, null, 2);
    this.jsonErrors = [];
    this.jsonValid  = true;
    this.jsonDirty  = false;
  }

  private applyJsonToRules(json: string): void {
    let entries: MappingEntry[];
    try { entries = JSON.parse(json); }
    catch { return; }

    // Simple reconcile: delete all active rules, then recreate
    this.saving = true;
    const deletes = this.rules.filter(r => r.active).map(r =>
      this.mappingService.deleteRule(this.pipelineId, r.id).toPromise()
    );

    Promise.all(deletes).then(() => {
      const creates = entries.map(e =>
        this.mappingService.createRule(this.pipelineId, {
          sourceField: e.sourceField,
          targetField: e.targetField,
          mappingType: e.mappingType,
          expression:  e.expression ?? '',
        } as MappingRuleRequest).toPromise()
      );
      return Promise.all(creates);
    }).then(() => {
      this.saving     = false;
      this.saveStatus = 'saved';
      this.jsonDirty  = false;
      this.loadRules();
      setTimeout(() => { this.saveStatus = 'idle'; this.cdr.detectChanges(); }, 2500);
      this.cdr.detectChanges();
    }).catch(() => {
      this.saving     = false;
      this.saveStatus = 'error';
      this.cdr.detectChanges();
    });
  }

  // ── Validation ──────────────────────────────────────────────────────────────

  validateJson(text: string): void {
    this.jsonErrors = [];
    let parsed: any;

    // 1. Syntax check
    try { parsed = JSON.parse(text); }
    catch (e: any) {
      this.jsonErrors.push(`Syntax error: ${e.message}`);
      this.jsonValid = false;
      this.setMonacoErrors(this.jsonErrors);
      return;
    }

    // 2. Schema check (lightweight)
    if (!Array.isArray(parsed)) {
      this.jsonErrors.push('Root must be an array of mapping rules.');
    } else {
      const validTypes = new Set(['FIELD_PLACEMENT','FORMAT_CHANGE','VALUE_TRANSFORM','CALCULATED_FIELD','RESTRUCTURING']);
      parsed.forEach((item: any, i: number) => {
        if (typeof item !== 'object' || item === null)
          this.jsonErrors.push(`[${i}] must be an object.`);
        else {
          if (!item.sourceField || typeof item.sourceField !== 'string')
            this.jsonErrors.push(`[${i}] sourceField is required (string).`);
          if (!item.targetField || typeof item.targetField !== 'string')
            this.jsonErrors.push(`[${i}] targetField is required (string).`);
          if (!validTypes.has(item.mappingType))
            this.jsonErrors.push(`[${i}] mappingType must be one of: ${[...validTypes].join(', ')}.`);
        }
      });
    }

    this.jsonValid = this.jsonErrors.length === 0;
    this.setMonacoErrors(this.jsonErrors);
    this.jsonText = text;
  }

  // ── Monaco ───────────────────────────────────────────────────────────────────

  private ensureMonacoLoaded(): void {
    if ((window as any).monaco) {
      this.monacoLoaded = true;
      return;
    }
    // Dynamically load Monaco from CDN if not present
    const script = document.createElement('script');
    script.src = 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs/loader.min.js';
    script.onload = () => {
      (window as any).require.config({
        paths: { vs: 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs' }
      });
      (window as any).require(['vs/editor/editor.main'], () => {
        this.monacoLoaded = true;
        this.zone.run(() => {
          this.cdr.detectChanges();
          if (this.mode === 'json') this.initMonaco();
        });
      });
    };
    document.head.appendChild(script);
  }

  private initMonaco(): void {
    if (!this.monacoLoaded || !this.monacoContainerRef) return;
    if (this.monacoEditor) { this.monacoEditor.dispose(); this.monacoEditor = null; }

    const container = this.monacoContainerRef.nativeElement;
    if (!container) return;

    // Register JSON schema for autocompletion + validation
    monaco.languages.json.jsonDefaults.setDiagnosticsOptions({
      validate: true,
      schemas: [{
        uri: 'http://miniESB/mapping-schema.json',
        fileMatch: ['*'],
        schema: MAPPING_JSON_SCHEMA,
      }],
    });

    this.monacoEditor = monaco.editor.create(container, {
      value:              this.jsonText,
      language:           'json',
      theme:              'vs',
      automaticLayout:    true,
      minimap:            { enabled: false },
      lineNumbers:        'on',
      scrollBeyondLastLine: false,
      fontSize:           13,
      fontFamily:         "'JetBrains Mono', 'Fira Code', monospace",
      padding:            { top: 8, bottom: 8 },
      tabSize:            2,
      wordWrap:           'on',
      formatOnPaste:      true,
      formatOnType:       false,
    });

    // Listen to content changes
    this.monacoEditor.onDidChangeModelContent(() => {
      const val = this.monacoEditor.getValue();
      this.jsonEdit$.next(val);
    });
  }

  private pushJsonToMonaco(): void {
    if (this.monacoEditor) {
      this.monacoEditor.setValue(this.jsonText);
    }
  }

  private setMonacoErrors(errors: string[]): void {
    if (!this.monacoEditor) return;
    const model = this.monacoEditor.getModel();
    if (!model) return;

    const markers = errors.map((msg, i) => ({
      severity: monaco.MarkerSeverity.Error,
      message:  msg,
      startLineNumber: 1,
      startColumn:     1,
      endLineNumber:   1,
      endColumn:       5,
    }));
    monaco.editor.setModelMarkers(model, 'miniESB', markers);
  }

  // ── Prettify ────────────────────────────────────────────────────────────────

  prettify(): void {
    if (!this.monacoEditor) return;
    this.monacoEditor.getAction('editor.action.formatDocument').run();
  }

  // ── Apply from JSON editor ──────────────────────────────────────────────────

  applyJson(): void {
    const text = this.monacoEditor ? this.monacoEditor.getValue() : this.jsonText;
    this.validateJson(text);
    if (!this.jsonValid) return;
    this.applyJsonToRules(text);
  }

  // ── Visual table helpers ─────────────────────────────────────────────────────
  trackRule(_: number, r: MappingRuleResponse): number { return r.id; }

  typeColors: Record<MappingType, string> = {
    FIELD_PLACEMENT:  '#3b82f6',
    FORMAT_CHANGE:    '#f59e0b',
    VALUE_TRANSFORM:  '#8b5cf6',
    CALCULATED_FIELD: '#10b981',
    RESTRUCTURING:    '#ef4444',
  };

  typeLabels: Record<MappingType, string> = {
    FIELD_PLACEMENT:  'Placement',
    FORMAT_CHANGE:    'Format',
    VALUE_TRANSFORM:  'Transform',
    CALCULATED_FIELD: 'Calculated',
    RESTRUCTURING:    'Restructuring',
  };
}