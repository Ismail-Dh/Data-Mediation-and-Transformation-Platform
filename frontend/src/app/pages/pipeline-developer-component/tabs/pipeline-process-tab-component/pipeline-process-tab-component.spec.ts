import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PipelineProcessTabComponent } from './pipeline-process-tab-component';

describe('PipelineProcessTabComponent', () => {
  let component: PipelineProcessTabComponent;
  let fixture: ComponentFixture<PipelineProcessTabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineProcessTabComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PipelineProcessTabComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
