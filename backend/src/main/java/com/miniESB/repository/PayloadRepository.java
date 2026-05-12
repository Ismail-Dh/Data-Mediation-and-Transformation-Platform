package com.miniESB.repository;

import com.miniESB.domain.entity.Payload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayloadRepository extends JpaRepository<Payload, Long> {
    List<Payload> findAllByPipelineId(Long pipelineId);
}