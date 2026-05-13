package com.miniESB.repository;

import com.miniESB.domain.entity.PipelineField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PipelineFieldRepository extends JpaRepository<PipelineField, Long> {

    
    List<PipelineField> findAllByPipelineId(Long pipelineId);
    boolean existsByPipelineIdAndFieldPath(Long pipelineId, String fieldPath);
}