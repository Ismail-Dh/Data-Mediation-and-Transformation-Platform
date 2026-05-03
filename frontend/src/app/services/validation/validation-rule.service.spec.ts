import { TestBed } from '@angular/core/testing';

import { ValidationRuleService } from './validation-rule.service';

describe('ValidationRuleService', () => {
  let service: ValidationRuleService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ValidationRuleService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
