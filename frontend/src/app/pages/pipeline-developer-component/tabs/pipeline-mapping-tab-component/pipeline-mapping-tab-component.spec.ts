import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PipelineMappingTabComponent } from './pipeline-mapping-tab-component';

describe('PipelineMappingTabComponent', () => {
  let component: PipelineMappingTabComponent;
  let fixture: ComponentFixture<PipelineMappingTabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineMappingTabComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PipelineMappingTabComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
