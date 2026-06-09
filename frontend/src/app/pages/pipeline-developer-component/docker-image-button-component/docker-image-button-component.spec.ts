import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DockerImageButtonComponent } from './docker-image-button-component';

describe('DockerImageButtonComponent', () => {
  let component: DockerImageButtonComponent;
  let fixture: ComponentFixture<DockerImageButtonComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DockerImageButtonComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DockerImageButtonComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
