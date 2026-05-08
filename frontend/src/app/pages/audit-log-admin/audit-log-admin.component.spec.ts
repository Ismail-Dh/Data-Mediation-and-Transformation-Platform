import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuditLogAdminComponent } from './audit-log-admin.component';

describe('AuditLogAdminComponent', () => {
  let component: AuditLogAdminComponent;
  let fixture: ComponentFixture<AuditLogAdminComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditLogAdminComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AuditLogAdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
