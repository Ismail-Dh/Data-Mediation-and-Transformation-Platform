package com.miniESB.repository;

import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineRepository extends JpaRepository<Pipeline, Long> {
    List<Pipeline> findByCreatedBy(User user);
}
