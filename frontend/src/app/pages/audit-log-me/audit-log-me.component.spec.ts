import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuditLogMeComponent } from './audit-log-me.component';

describe('AuditLogMeComponent', () => {
  let component: AuditLogMeComponent;
  let fixture: ComponentFixture<AuditLogMeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditLogMeComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AuditLogMeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
