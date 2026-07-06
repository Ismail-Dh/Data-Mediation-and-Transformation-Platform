import { TestBed } from '@angular/core/testing';

import { ProcessApiService } from './process-api-service';

describe('ProcessApiService', () => {
  let service: ProcessApiService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ProcessApiService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
