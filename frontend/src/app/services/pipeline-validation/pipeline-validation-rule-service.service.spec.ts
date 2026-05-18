import { TestBed } from '@angular/core/testing';

import { PipelineValidationRuleServiceService } from './pipeline-validation-rule-service.service';

describe('PipelineValidationRuleServiceService', () => {
  let service: PipelineValidationRuleServiceService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(PipelineValidationRuleServiceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
