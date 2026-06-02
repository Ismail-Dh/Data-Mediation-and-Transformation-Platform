import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MappingJsonEditor } from './mapping-json-editor';

describe('MappingJsonEditor', () => {
  let component: MappingJsonEditor;
  let fixture: ComponentFixture<MappingJsonEditor>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MappingJsonEditor]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MappingJsonEditor);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
