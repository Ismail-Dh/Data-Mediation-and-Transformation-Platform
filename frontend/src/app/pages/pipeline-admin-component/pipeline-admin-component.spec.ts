import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PipelineAdminComponent } from './pipeline-admin-component';

describe('PipelineAdminComponent', () => {
  let component: PipelineAdminComponent;
  let fixture: ComponentFixture<PipelineAdminComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineAdminComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PipelineAdminComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
