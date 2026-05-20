import { TestBed } from '@angular/core/testing';

import { MappingRuleService } from './mapping-rule-service';

describe('MappingRuleService', () => {
  let service: MappingRuleService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(MappingRuleService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
