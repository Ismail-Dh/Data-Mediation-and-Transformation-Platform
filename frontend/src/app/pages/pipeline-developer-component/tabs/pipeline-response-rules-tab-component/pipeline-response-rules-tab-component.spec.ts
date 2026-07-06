import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PipelineResponseRulesTabComponent } from './pipeline-response-rules-tab-component';

describe('PipelineResponseRulesTabComponent', () => {
  let component: PipelineResponseRulesTabComponent;
  let fixture: ComponentFixture<PipelineResponseRulesTabComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineResponseRulesTabComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PipelineResponseRulesTabComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
