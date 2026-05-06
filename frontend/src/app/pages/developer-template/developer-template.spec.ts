import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeveloperTemplate } from './developer-template';

describe('DeveloperTemplate', () => {
  let component: DeveloperTemplate;
  let fixture: ComponentFixture<DeveloperTemplate>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeveloperTemplate]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DeveloperTemplate);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
