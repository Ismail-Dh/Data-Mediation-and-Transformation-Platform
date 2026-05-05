package com.miniESB.repository;

import com.miniESB.domain.entity.MappingTemplate;
import com.miniESB.domain.enums.TemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MappingTemplateRepository extends JpaRepository<MappingTemplate, Long> {

    List<MappingTemplate> findAllByStatus(TemplateStatus status);

    boolean existsByNameAndStatus(String name, TemplateStatus status);
}