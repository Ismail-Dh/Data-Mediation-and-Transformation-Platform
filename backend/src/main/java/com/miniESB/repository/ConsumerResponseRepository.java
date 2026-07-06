package com.miniESB.repository;

import com.miniESB.domain.entity.ConsumerResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsumerResponseRepository extends JpaRepository<ConsumerResponse, Long> {

    Optional<ConsumerResponse> findByPayloadId(Long payloadId);

    List<ConsumerResponse> findAllByPipelineIdOrderByBuiltAtDesc(Long pipelineId);
}