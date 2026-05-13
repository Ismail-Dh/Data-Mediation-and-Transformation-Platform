import { TestBed } from '@angular/core/testing';

import { PipelineFieldService } from './pipeline-field-service';

describe('PipelineFieldService', () => {
  let service: PipelineFieldService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(PipelineFieldService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
