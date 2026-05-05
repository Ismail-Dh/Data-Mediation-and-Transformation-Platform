package com.miniESB.repository;

import com.miniESB.domain.entity.ValidationTemplate;
import com.miniESB.domain.enums.TemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationTemplateRepository extends JpaRepository<ValidationTemplate, Long> {

    List<ValidationTemplate> findAllByStatus(TemplateStatus status);

    boolean existsByNameAndStatus(String name, TemplateStatus status);
}