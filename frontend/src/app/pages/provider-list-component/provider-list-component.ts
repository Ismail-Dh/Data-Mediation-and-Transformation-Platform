import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators
} from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { ProviderService } from '../../services/provider/provider-service';
import { Provider } from '../../models/provider';

@Component({
  selector: 'app-provider-list-component',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './provider-list-component.html',
  styleUrl: './provider-list-component.scss',
})
export class ProviderListComponent implements OnInit {

  providers: Provider[] = [];
  filtered: Provider[] = [];
  searchQuery = '';

  showModal = false;
  editingId: number | null = null;
  deletingProvider: Provider | null = null;

  form!: FormGroup;

  readonly PROTOCOLS = ['REST', 'SOAP', 'MQTT', 'AMQP', 'GRPC','HTTPS'];

  constructor(
    private providerService: ProviderService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.buildForm();
    this.load();
  }

  private buildForm(): void {
    this.form = this.fb.group({
      name:     ['', Validators.required],
      endpoint: ['', Validators.required],
      protocol: ['', Validators.required],
      timeout:  [5000, [Validators.required, Validators.min(1)]]
    });
  }

  private load(): void {
    this.providerService.getAll().subscribe(data => {
      this.providers = data;
      this.applyFilter();
      this.cdr.detectChanges();
    });
  }

  applyFilter(): void {
    const q = this.searchQuery.trim().toLowerCase();
    this.filtered = q
      ? this.providers.filter(p =>
          p.name.toLowerCase().includes(q) ||
          p.endpoint.toLowerCase().includes(q) ||
          p.protocol.toLowerCase().includes(q)
        )
      : [...this.providers];
    this.cdr.detectChanges();
  }

  openCreate(): void {
    this.editingId = null;
    this.form.reset({ timeout: 5000 });
    this.showModal = true;
    this.cdr.detectChanges();
  }

  openEdit(p: Provider): void {
    this.editingId = p.id;
    this.form.patchValue({
      name:     p.name,
      endpoint: p.endpoint,
      protocol: p.protocol,
      timeout:  p.timeout
    });
    this.showModal = true;
    this.cdr.detectChanges();
  }

  closeModal(): void {
    this.showModal = false;
    this.editingId = null;
    this.cdr.detectChanges();
  }

  save(): void {
    if (this.form.invalid) return;
    const val = this.form.value;

    if (this.editingId !== null) {
      this.providerService.update(this.editingId, val).subscribe(() => {
        this.load();
        this.closeModal();
        this.cdr.detectChanges();
      });
    } else {
      this.providerService.create(val).subscribe(() => {
        this.load();
        this.closeModal();
        this.cdr.detectChanges();
      });
    }
  }

  askDelete(p: Provider): void {
    this.deletingProvider = p;
    this.cdr.detectChanges();
  }

  confirmDelete(): void {
    if (!this.deletingProvider) return;
    this.providerService.delete(this.deletingProvider.id).subscribe(() => {
      this.deletingProvider = null;
      this.load();
      this.cdr.detectChanges();
    });
  }

  cancelDelete(): void {
    this.deletingProvider = null;
    this.cdr.detectChanges();
  }
}
