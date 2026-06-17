import { TestBed } from '@angular/core/testing';

import { DockerImageService } from './docker-image-service';

describe('DockerImageService', () => {
  let service: DockerImageService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DockerImageService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
