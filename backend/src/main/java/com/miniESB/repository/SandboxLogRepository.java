// repository/SandboxLogRepository.java
package com.miniESB.repository;

import com.miniESB.domain.entity.SandboxLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SandboxLogRepository extends JpaRepository<SandboxLog, Long> {
    Page<SandboxLog> findByPipelineIdOrderByExecutedAtDesc(Long pipelineId, Pageable pageable);
}