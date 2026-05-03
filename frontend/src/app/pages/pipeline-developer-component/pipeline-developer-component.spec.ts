import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PipelineDeveloperComponent } from './pipeline-developer-component';

describe('PipelineDeveloperComponent', () => {
  let component: PipelineDeveloperComponent;
  let fixture: ComponentFixture<PipelineDeveloperComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineDeveloperComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PipelineDeveloperComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
