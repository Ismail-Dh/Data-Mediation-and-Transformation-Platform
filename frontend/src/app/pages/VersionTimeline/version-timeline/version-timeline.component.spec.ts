import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VersionTimelineComponent } from './version-timeline.component';

describe('VersionTimelineComponent', () => {
  let component: VersionTimelineComponent;
  let fixture: ComponentFixture<VersionTimelineComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VersionTimelineComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(VersionTimelineComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
